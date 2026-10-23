package com.matan.vault

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.matan.vault.data.Category
import com.matan.vault.data.DocumentEntity
import com.matan.vault.data.PageEntity
import com.matan.vault.data.VaultDatabase
import com.matan.vault.ocr.DocParser
import com.matan.vault.ocr.Extracted
import com.matan.vault.ocr.Importer
import com.matan.vault.security.FileStore
import com.matan.vault.share.ShareHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The document being created or edited. The form lives here so OCR results can fill it while the user types. */
data class EditState(
    val docId: Long?,
    val doc: DocumentEntity,
    val pages: List<String>,
    val originalPages: List<String> = emptyList(),
    val newFiles: Set<String> = emptySet(),
    val extracted: Extracted? = null,
    val busy: Boolean = false,
)

class VaultViewModel(app: Application) : AndroidViewModel(app) {
    private val db = VaultDatabase.get(app)
    private val docs = db.documents()
    private val pagesDao = db.pages()
    private val ctx get() = getApplication<Application>()

    val documents: StateFlow<List<DocumentEntity>> =
        docs.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val covers: StateFlow<Map<Long, String>> =
        pagesDao.observeCovers().map { list -> list.associate { it.documentId to it.fileName } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _edit = MutableStateFlow<EditState?>(null)
    val edit: StateFlow<EditState?> = _edit.asStateFlow()

    /** URIs that arrived through the Android share sheet, waiting for unlock. */
    val pendingShare = MutableStateFlow<List<Uri>>(emptyList())
    val pendingOpenDoc = MutableStateFlow<Long?>(null)

    val message = MutableStateFlow<String?>(null)

    fun observeDoc(id: Long) = docs.observe(id)
    fun observePages(id: Long) = pagesDao.observeFor(id)

    // ---------- creating / editing ----------

    fun startNew(uris: List<Uri>, template: DocumentEntity? = null) {
        val base = template ?: DocumentEntity(title = "", category = Category.OTHER.name)
        _edit.value = EditState(docId = null, doc = base, pages = emptyList(), busy = uris.isNotEmpty())
        if (uris.isNotEmpty()) addPages(uris)
    }

    fun startEdit(id: Long) {
        viewModelScope.launch {
            val doc = docs.get(id) ?: return@launch
            val pages = pagesDao.pagesFor(id).map { it.fileName }
            _edit.value = EditState(docId = id, doc = doc, pages = pages, originalPages = pages)
        }
    }

    fun updateForm(change: (DocumentEntity) -> DocumentEntity) {
        _edit.update { it?.copy(doc = change(it.doc)) }
    }

    fun addPages(uris: List<Uri>) {
        _edit.update { it?.copy(busy = true) }
        viewModelScope.launch {
            val (names, text) = withContext(Dispatchers.Default) {
                val bitmaps = Importer.loadPages(ctx, uris)
                val names = bitmaps.map { FileStore.write(ctx, Importer.toJpeg(it)) }
                val text = Importer.recognize(bitmaps)
                bitmaps.forEach { it.recycle() }
                names to text
            }
            if (names.isEmpty()) message.value = "לא הצלחתי לקרוא את הקובץ"
            _edit.update { st ->
                st ?: return@update null
                val fullText = listOf(st.doc.ocrText, text).filter { it.isNotBlank() }.joinToString("\n")
                val ex = DocParser.parse(fullText)
                st.copy(
                    pages = st.pages + names,
                    newFiles = st.newFiles + names,
                    doc = applyExtracted(st.doc.copy(ocrText = fullText), ex),
                    extracted = ex,
                    busy = false,
                )
            }
        }
    }

    /** Fills only empty fields, so OCR never overwrites what the user typed. */
    private fun applyExtracted(d: DocumentEntity, ex: Extracted): DocumentEntity {
        val cat = if (d.category == Category.OTHER.name && ex.category != null) ex.category else d.cat()
        val catChanged = cat != d.cat()
        return d.copy(
            title = d.title.ifBlank { ex.title ?: "" },
            category = cat.name,
            leadDays = if (catChanged && d.id == 0L) cat.defaultLeadDays.coerceAtLeast(7) else d.leadDays,
            owner = d.owner.ifBlank { ex.holderName ?: "" },
            docNumber = d.docNumber.ifBlank { ex.docNumber ?: "" },
            issuer = d.issuer.ifBlank { ex.issuer ?: "" },
            issueDate = d.issueDate ?: ex.issueDate?.toEpochDay(),
            expiryDate = d.expiryDate ?: ex.expiryDate?.toEpochDay(),
            amount = d.amount.ifBlank { ex.amount ?: "" },
        )
    }

    fun removePage(index: Int) {
        _edit.update { st -> st?.copy(pages = st.pages.filterIndexed { i, _ -> i != index }) }
    }

    fun movePage(index: Int, delta: Int) {
        _edit.update { st ->
            st ?: return@update null
            val to = index + delta
            if (to !in st.pages.indices) return@update st
            val list = st.pages.toMutableList()
            val p = list.removeAt(index)
            list.add(to, p)
            st.copy(pages = list)
        }
    }

    fun cancelEdit() {
        val st = _edit.value ?: return
        _edit.value = null
        viewModelScope.launch(Dispatchers.IO) { st.newFiles.forEach { FileStore.delete(ctx, it) } }
    }

    fun saveEdit(onSaved: (Long) -> Unit) {
        val st = _edit.value ?: return
        val doc = st.doc.copy(title = st.doc.title.ifBlank { st.doc.cat().label })
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) {
                val id = if (st.docId == null) docs.insert(doc.copy(id = 0)) else {
                    val expiryChanged = docs.get(st.docId)?.expiryDate != doc.expiryDate
                    docs.update(doc.copy(id = st.docId, lastNotifiedDay = if (expiryChanged) null else doc.lastNotifiedDay))
                    st.docId
                }
                pagesDao.deleteFor(id)
                pagesDao.insertAll(st.pages.mapIndexed { i, f -> PageEntity(documentId = id, position = i, fileName = f) })
                val keep = st.pages.toSet()
                (st.originalPages + st.newFiles).filter { it !in keep }.forEach { FileStore.delete(ctx, it) }
                id
            }
            _edit.value = null
            onSaved(id)
        }
    }

    // ---------- document actions ----------

    fun toggleFavorite(doc: DocumentEntity) = viewModelScope.launch { docs.update(doc.copy(favorite = !doc.favorite)) }

    fun setArchived(doc: DocumentEntity, archived: Boolean) =
        viewModelScope.launch { docs.update(doc.copy(archived = archived, favorite = doc.favorite && !archived)) }

    fun setReminders(doc: DocumentEntity, on: Boolean) =
        viewModelScope.launch { docs.update(doc.copy(remindersOn = on)) }

    fun snooze(doc: DocumentEntity, days: Int) = viewModelScope.launch {
        // pretend we notified "days" from now minus the repeat interval
        val repeat = doc.cat().repeatDays.coerceAtLeast(1)
        val today = java.time.LocalDate.now().toEpochDay()
        docs.update(doc.copy(lastNotifiedDay = today + days - repeat))
        message.value = "התזכורת נדחתה ב-$days ימים"
    }

    /** "I renewed it": archive the old one and open a new draft with the same details. */
    fun renew(doc: DocumentEntity) {
        viewModelScope.launch { docs.update(doc.copy(archived = true, favorite = false)) }
        startNew(
            emptyList(),
            DocumentEntity(
                title = doc.title, category = doc.category, owner = doc.owner, tags = doc.tags,
                issuer = doc.issuer, leadDays = doc.leadDays, favorite = doc.favorite,
            )
        )
    }

    fun delete(doc: DocumentEntity) = viewModelScope.launch(Dispatchers.IO) {
        pagesDao.pagesFor(doc.id).forEach { FileStore.delete(ctx, it.fileName) }
        pagesDao.deleteFor(doc.id)
        docs.delete(doc)
    }

    fun share(activity: android.content.Context, doc: DocumentEntity, asPdf: Boolean, watermark: String?) =
        viewModelScope.launch {
            val pages = pagesDao.pagesFor(doc.id)
            if (pages.isEmpty()) {
                message.value = "אין עמודים לשתף"
                return@launch
            }
            try {
                ShareHelper.share(activity, doc, pages, asPdf, watermark)
            } catch (e: Exception) {
                message.value = "השיתוף נכשל: ${e.message}"
            }
        }
}

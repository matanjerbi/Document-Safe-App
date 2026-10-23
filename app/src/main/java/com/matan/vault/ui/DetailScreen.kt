package com.matan.vault.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NotificationsPaused
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.matan.vault.VaultViewModel
import com.matan.vault.data.Category
import com.matan.vault.data.DocumentEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    vm: VaultViewModel,
    id: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onViewPage: (Int) -> Unit,
    onRenew: () -> Unit,
) {
    val doc by vm.observeDoc(id).collectAsState(initial = null)
    val pages by vm.observePages(id).collectAsState(initial = emptyList())
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }
    val d = doc

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(d?.title ?: "") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "חזרה") } },
                actions = {
                    if (d != null) {
                        IconButton(onClick = { vm.toggleFavorite(d) }) {
                            if (d.favorite) Icon(Icons.Filled.Star, "הסר ממועדפים", tint = Gold)
                            else Icon(Icons.Outlined.StarBorder, "הוסף למועדפים")
                        }
                        IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "עריכה") }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "עוד") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (d.archived) "החזר מהארכיון" else "העבר לארכיון") },
                                    leadingIcon = { Icon(if (d.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive, null) },
                                    onClick = { menu = false; vm.setArchived(d, !d.archived) },
                                )
                                DropdownMenuItem(
                                    text = { Text("מחק") },
                                    leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                    onClick = { menu = false; confirmDelete = true },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        if (d == null) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)
        ) {
            if (pages.isNotEmpty()) {
                val pager = rememberPagerState { pages.size }
                HorizontalPager(
                    state = pager,
                    contentPadding = PaddingValues(horizontal = 32.dp),
                    pageSpacing = 12.dp,
                    modifier = Modifier.fillMaxWidth().height(340.dp),
                ) { i ->
                    EncryptedImage(
                        pages[i].fileName,
                        Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).clickable { onViewPage(i) },
                        maxSide = 1200,
                        contentScale = ContentScale.Fit,
                    )
                }
                if (pages.size > 1) {
                    Text(
                        "עמוד ${pager.currentPage + 1} מתוך ${pages.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = { showShare = true }, modifier = Modifier.weight(1f), enabled = pages.isNotEmpty()) {
                    Icon(Icons.Outlined.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("שיתוף")
                }
                if (d.expiryDate != null) {
                    FilledTonalButton(onClick = onRenew, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Autorenew, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("חידשתי")
                    }
                }
            }

            if (d.expiryDate != null) ExpiryCard(vm, d)

            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            ) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    InfoRow("קטגוריה", d.cat().label)
                    if (d.owner.isNotBlank()) InfoRow("של", d.owner)
                    if (d.docNumber.isNotBlank()) InfoRow("מספר", d.docNumber, copy = true)
                    if (d.issuer.isNotBlank()) InfoRow("גורם מנפיק", d.issuer)
                    d.issueDate?.let { InfoRow(if (d.cat() == Category.RECEIPT) "תאריך רכישה" else "תאריך הנפקה", it.epochDayFmt()) }
                    d.expiryDate?.let { InfoRow(if (d.cat() == Category.RECEIPT) "אחריות עד" else "בתוקף עד", it.epochDayFmt()) }
                    if (d.amount.isNotBlank()) InfoRow("סכום", "₪${d.amount}", copy = true)
                    if (d.tags.isNotBlank()) InfoRow("תגיות", d.tagList().joinToString(", "))
                    if (d.notes.isNotBlank()) InfoRow("הערות", d.notes)
                    if (d.archived) InfoRow("מצב", "בארכיון")
                }
            }
            if (d.ocrText.isNotBlank()) {
                TextButton(onClick = { showText = !showText }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(if (showText) "הסתר טקסט שזוהה" else "הצג טקסט שזוהה מהמסמך")
                }
                if (showText) {
                    Text(
                        d.ocrText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
        }
    }

    if (confirmDelete && d != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("למחוק את \"${d.title}\"?") },
            text = { Text("המסמך וכל העמודים שלו יימחקו מהמכשיר. אי אפשר לבטל את זה.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete(d); onBack() }) { Text("מחק", color = Bad) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("ביטול") } },
        )
    }
    if (showShare && d != null) {
        ShareDialog(
            onDismiss = { showShare = false },
            onShare = { pdf, mark -> showShare = false; vm.share(context, d, pdf, mark) },
        )
    }
}

@Composable
private fun ExpiryCard(vm: VaultViewModel, d: DocumentEntity) {
    val c = expiryColor(d.expiryState())
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = c.copy(alpha = 0.08f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(expiryText(d) ?: "", style = MaterialTheme.typography.titleLarge, color = c)
            Text(
                "תזכורת ${d.leadDays} ימים לפני, ${d.expiryDate!!.epochDayFmt()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("תזכורות פעילות", Modifier.weight(1f))
                Switch(checked = d.remindersOn, onCheckedChange = { vm.setReminders(d, it) })
            }
            if (d.remindersOn && d.expiryState() != com.matan.vault.data.ExpiryState.OK) {
                OutlinedButton(onClick = { vm.snooze(d, 7) }) {
                    Icon(Icons.Outlined.NotificationsPaused, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("דחה תזכורת בשבוע")
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, copy: Boolean = false) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(110.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (copy) {
            IconButton(onClick = { copyToClipboard(context, value) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.ContentCopy, "העתק", Modifier.size(18.dp))
            }
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java)
    cm.setPrimaryClip(ClipData.newPlainText("vault", text))
    Toast.makeText(context, "הועתק", Toast.LENGTH_SHORT).show()
}

@Composable
fun ShareDialog(onDismiss: () -> Unit, onShare: (asPdf: Boolean, watermark: String?) -> Unit) {
    var pdf by remember { mutableStateOf(true) }
    var mark by remember { mutableStateOf(false) }
    var markText by remember { mutableStateOf("העתק לשימוש ב- בלבד") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("שיתוף מסמך") },
        text = {
            Column {
                Row(Modifier.fillMaxWidth().clickable { pdf = true }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(pdf, { pdf = true }); Text("קובץ PDF אחד")
                }
                Row(Modifier.fillMaxWidth().clickable { pdf = false }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(!pdf, { pdf = false }); Text("תמונות")
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("סימן מים", Modifier.weight(1f))
                    Switch(mark, { mark = it })
                }
                if (mark) {
                    OutlinedTextField(
                        value = markText,
                        onValueChange = { markText = it },
                        label = { Text("טקסט על המסמך") },
                        supportingText = { Text("מגן מפני שימוש לרעה בצילום של תעודה") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = { Button(onClick = { onShare(pdf, if (mark) markText else null) }) { Text("שתף") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

/** Full-screen page with pinch to zoom. */
@Composable
fun PageViewer(vm: VaultViewModel, id: Long, start: Int, onBack: () -> Unit) {
    val pages by vm.observePages(id).collectAsState(initial = emptyList())
    if (pages.isEmpty()) return
    val pager = rememberPagerState(initialPage = start.coerceIn(0, pages.size - 1)) { pages.size }
    Box(Modifier.fillMaxSize().padding(0.dp)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { i ->
            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offset = if (scale == 1f) Offset.Zero else offset + pan
                    }
                }
            ) {
                EncryptedImage(
                    pages[i].fileName,
                    Modifier.fillMaxSize().graphicsLayer(
                        scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y
                    ),
                    maxSide = 2400,
                    contentScale = ContentScale.Fit,
                )
            }
        }
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "חזרה", tint = Color.Black)
        }
    }
}

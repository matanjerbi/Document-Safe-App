package com.matan.vault.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.matan.vault.EditState
import com.matan.vault.VaultViewModel
import com.matan.vault.data.Category
import com.matan.vault.data.DocumentEntity
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    vm: VaultViewModel,
    st: EditState,
    owners: List<String>,
    onDone: (Long) -> Unit,
    onCancel: () -> Unit,
) {
    val d = st.doc
    val sources = rememberSources { vm.addPages(it) }
    BackHandler { onCancel() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (st.docId == null) "מסמך חדש" else "עריכה") },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Outlined.Close, "ביטול") } },
                actions = {
                    TextButton(onClick = { vm.saveEdit(onDone) }, enabled = !st.busy) {
                        Text("שמור", style = MaterialTheme.typography.titleMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(bottom = 40.dp)
        ) {
            // pages
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(st.pages, key = { _, f -> f }) { i, f ->
                    Box {
                        EncryptedImage(f, Modifier.size(width = 96.dp, height = 128.dp).clip(RoundedCornerShape(12.dp)), maxSide = 360)
                        FilledIconButton(
                            onClick = { vm.removePage(i) },
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(26.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Ink.copy(alpha = 0.6f)),
                        ) { Icon(Icons.Outlined.Close, "הסר עמוד", Modifier.size(16.dp)) }
                        if (st.pages.size > 1) {
                            Row(Modifier.align(Alignment.BottomCenter)) {
                                if (i > 0) IconButton(onClick = { vm.movePage(i, -1) }, Modifier.size(28.dp)) {
                                    Icon(Icons.Outlined.ChevronRight, "הזז ימינה")
                                }
                                if (i < st.pages.size - 1) IconButton(onClick = { vm.movePage(i, 1) }, Modifier.size(28.dp)) {
                                    Icon(Icons.Outlined.ChevronLeft, "הזז שמאלה")
                                }
                            }
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedCard(
                            onClick = sources.scan,
                            modifier = Modifier.size(width = 96.dp, height = 60.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Icon(Icons.Outlined.DocumentScanner, null)
                                Text("סריקה", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        OutlinedCard(
                            onClick = sources.pickFiles,
                            modifier = Modifier.size(width = 96.dp, height = 60.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Icon(Icons.Outlined.FolderOpen, null)
                                Text("מקבצים", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            if (st.busy) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text("קורא את המסמך ומחפש תאריכים ומספרים…", style = MaterialTheme.typography.labelMedium)
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
                }
            }

            DetectedBanner(st)

            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = d.title, onValueChange = { v -> vm.updateForm { it.copy(title = v) } },
                    label = { Text("שם המסמך") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                CategoryPicker(d.cat()) { c ->
                    vm.updateForm {
                        it.copy(
                            category = c.name,
                            leadDays = c.defaultLeadDays.coerceAtLeast(7),
                            title = if (it.title.isBlank() || it.title == it.cat().label) c.label else it.title,
                        )
                    }
                }
                OwnerField(d.owner, owners) { v -> vm.updateForm { it.copy(owner = v) } }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DateField(
                        if (d.cat() == Category.RECEIPT) "תאריך רכישה" else "תאריך הנפקה",
                        d.issueDate, { v -> vm.updateForm { it.copy(issueDate = v) } }, Modifier.weight(1f),
                    )
                    DateField(
                        if (d.cat() == Category.RECEIPT) "אחריות עד" else "בתוקף עד",
                        d.expiryDate, { v -> vm.updateForm { it.copy(expiryDate = v) } }, Modifier.weight(1f),
                    )
                }
                if (d.cat() == Category.RECEIPT && d.expiryDate == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(12 to "אחריות לשנה", 24 to "שנתיים", 36 to "3 שנים").forEach { (months, label) ->
                            SuggestionChip(
                                onClick = {
                                    val base = d.issue() ?: LocalDate.now()
                                    vm.updateForm { it.copy(expiryDate = base.plusMonths(months.toLong()).toEpochDay()) }
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                }
                if (d.expiryDate != null) {
                    OutlinedTextField(
                        value = d.leadDays.toString(),
                        onValueChange = { v -> v.filter { it.isDigit() }.take(3).toIntOrNull()?.let { n -> vm.updateForm { it.copy(leadDays = n) } } },
                        label = { Text("להזכיר כמה ימים לפני") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = d.docNumber, onValueChange = { v -> vm.updateForm { it.copy(docNumber = v) } },
                    label = { Text(numberLabel(d.cat())) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = d.issuer, onValueChange = { v -> vm.updateForm { it.copy(issuer = v) } },
                    label = { Text(if (d.cat() == Category.RECEIPT) "חנות" else "גורם מנפיק") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                if (d.cat() == Category.RECEIPT || d.cat() == Category.INSURANCE || d.cat() == Category.FINANCE || d.amount.isNotBlank()) {
                    OutlinedTextField(
                        value = d.amount, onValueChange = { v -> vm.updateForm { it.copy(amount = v) } },
                        label = { Text("סכום (₪)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = d.tags, onValueChange = { v -> vm.updateForm { it.copy(tags = v) } },
                    label = { Text("תגיות (מופרדות בפסיק)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = d.notes, onValueChange = { v -> vm.updateForm { it.copy(notes = v) } },
                    label = { Text("הערות (טלפון שירות לקוחות, מיקום המקור…)") },
                    minLines = 2, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun numberLabel(c: Category) = when (c) {
    Category.ID -> "מספר תעודת זהות"
    Category.PASSPORT -> "מספר דרכון"
    Category.LICENSE -> "מספר רישיון"
    Category.VEHICLE -> "מספר רכב"
    Category.INSURANCE -> "מספר פוליסה"
    Category.RECEIPT -> "מספר קבלה / מספר סידורי"
    else -> "מספר מסמך"
}

@Composable
private fun DetectedBanner(st: EditState) {
    val ex = st.extracted ?: return
    val found = buildList {
        ex.category?.let { add("סוג: ${it.label}") }
        ex.expiryDate?.let { add("תוקף: ${it.fmt()}") }
        ex.issueDate?.let { add("הנפקה: ${it.fmt()}") }
        ex.docNumber?.let { add("מספר: $it") }
        ex.holderName?.let { add("שם: $it") }
        ex.birthDate?.let { add("לידה: ${it.fmt()}") }
        ex.amount?.let { add("סכום: ₪$it") }
    }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)),
        border = BorderStroke(1.dp, Gold.copy(alpha = 0.5f)),
    ) {
        Row(Modifier.padding(14.dp)) {
            Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(10.dp))
            Column {
                if (found.isEmpty()) {
                    Text("לא זוהו פרטים אוטומטית", style = MaterialTheme.typography.titleMedium)
                    Text("אפשר למלא ידנית. צילום ישר ומואר משפר את הזיהוי.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        if (ex.fromMrz) "נקרא מאזור הקוד בתחתית המסמך" else "זוהה מתוך המסמך",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    found.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    Spacer(Modifier.height(4.dp))
                    Text("מילאתי את השדות. בדוק ותקן אם צריך.", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryPicker(selected: Category, onPick: (Category) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("קטגוריה") },
            leadingIcon = { Icon(selected.icon(), null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Category.entries.forEach { c ->
                DropdownMenuItem(
                    text = { Text(c.label) },
                    leadingIcon = { Icon(c.icon(), null) },
                    onClick = { open = false; onPick(c) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OwnerField(value: String, owners: List<String>, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val options = owners.filter { it != value && (value.isBlank() || it.contains(value, ignoreCase = true)) }
    ExposedDropdownMenuBox(expanded = open && options.isNotEmpty(), onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it); open = true },
            label = { Text("של מי המסמך") },
            placeholder = { Text("אני, בת הזוג, אבא…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(expanded = open && options.isNotEmpty(), onDismissRequest = { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { onChange(o); open = false }) }
        }
    }
}

@Suppress("unused")
private fun DocumentEntity.hasAnyDate() = issueDate != null || expiryDate != null

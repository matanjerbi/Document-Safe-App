package com.matan.vault.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.matan.vault.data.Category
import com.matan.vault.data.DocumentEntity
import com.matan.vault.data.ExpiryState

@Composable
fun HomeScreen(
    documents: List<DocumentEntity>,
    covers: Map<Long, String>,
    importing: Boolean,
    snackbar: SnackbarHostState,
    onOpen: (Long) -> Unit,
    onScan: () -> Unit,
    onFiles: () -> Unit,
    onTimeline: () -> Unit,
    onLock: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("ALL") }
    var owner by rememberSaveable { mutableStateOf("") }

    val active = documents.filter { !it.archived }
    val owners = active.map { it.owner.trim() }.filter { it.isNotEmpty() }.distinct()
    val usedCategories = Category.entries.filter { c -> active.any { it.category == c.name } }
    val q = query.trim().lowercase()

    val visible = documents.filter { d ->
        (if (filter == "ARCHIVE") d.archived else !d.archived) &&
            (filter == "ALL" || filter == "ARCHIVE" || d.category == filter) &&
            (owner.isEmpty() || d.owner.trim() == owner) &&
            (q.isEmpty() || listOf(d.title, d.cat().label, d.owner, d.tags, d.docNumber, d.issuer, d.notes, d.ocrText)
                .any { it.lowercase().contains(q) })
    }
    val soon = active
        .filter { it.expiryState() == ExpiryState.SOON || it.expiryState() == ExpiryState.EXPIRED }
        .sortedBy { it.expiryDate }
    val favorites = active.filter { it.favorite }
    val browsing = q.isEmpty() && filter == "ALL" && owner.isEmpty()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(onClick = onFiles, containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                    Icon(Icons.Outlined.FolderOpen, "ייבוא מקבצים")
                }
                ExtendedFloatingActionButton(
                    onClick = onScan,
                    icon = { Icon(Icons.Outlined.DocumentScanner, null) },
                    text = { Text("סריקה") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(bottom = 140.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("הכספת", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "${active.size} מסמכים" + if (soon.isNotEmpty()) " · ${soon.size} דורשים טיפול" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onTimeline) { Icon(Icons.Outlined.Timeline, "ציר תפוגות") }
                    IconButton(onClick = onLock) { Icon(Icons.Outlined.Lock, "נעל") }
                }
            }
            item {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("חיפוש: מקרר, דרכון, מספר פוליסה…") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            if (importing) {
                item {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                        Text("קורא את המסמך…", style = MaterialTheme.typography.labelMedium)
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
                    }
                }
            }
            if (documents.isEmpty()) {
                item { EmptyState() }
            }
            if (browsing && soon.isNotEmpty()) {
                item { SectionTitle("דורש טיפול") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(soon, key = { it.id }) { d -> SoonCard(d, covers[d.id]) { onOpen(d.id) } }
                    }
                }
            }
            if (browsing && favorites.isNotEmpty()) {
                item { SectionTitle("מועדפים") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(favorites, key = { it.id }) { d -> FavoriteTile(d, covers[d.id]) { onOpen(d.id) } }
                    }
                }
            }
            if (documents.isNotEmpty()) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item { FilterChip(filter == "ALL", { filter = "ALL" }, { Text("הכול") }) }
                        items(usedCategories) { c ->
                            FilterChip(filter == c.name, { filter = if (filter == c.name) "ALL" else c.name }, { Text(c.label) },
                                leadingIcon = { Icon(c.icon(), null, Modifier.size(18.dp)) })
                        }
                        if (documents.any { it.archived }) {
                            item { FilterChip(filter == "ARCHIVE", { filter = if (filter == "ARCHIVE") "ALL" else "ARCHIVE" }, { Text("ארכיון") }) }
                        }
                    }
                }
                if (owners.size > 1) {
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(owners) { o -> FilterChip(owner == o, { owner = if (owner == o) "" else o }, { Text(o) }) }
                        }
                    }
                }
            }
            items(visible, key = { it.id }) { d -> DocRow(d, covers[d.id]) { onOpen(d.id) } }
            if (documents.isNotEmpty() && visible.isEmpty()) {
                item {
                    Text(
                        "לא נמצאו מסמכים",
                        Modifier.fillMaxWidth().padding(32.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.DocumentScanner, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(20.dp))
        Text("הכספת ריקה", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "סרוק תעודת זהות, דרכון או קבלה. האפליקציה תקרא תאריכי תוקף ומספרי מסמך ותזכיר לך לפני שמשהו פג. אפשר גם לשתף לכאן PDF מוואטסאפ או מהמייל.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SoonCard(d: DocumentEntity, cover: String?, onClick: () -> Unit) {
    val c = expiryColor(d.expiryState())
    Card(
        onClick = onClick,
        modifier = Modifier.width(220.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, c.copy(alpha = 0.35f)),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            EncryptedImage(cover, Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)), maxSide = 200)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(d.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                ExpiryChip(d)
            }
        }
    }
}

@Composable
private fun FavoriteTile(d: DocumentEntity, cover: String?, onClick: () -> Unit) {
    Column(Modifier.width(112.dp).clickable(onClick = onClick)) {
        Box {
            EncryptedImage(cover, Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(14.dp)), maxSide = 360)
            Icon(Icons.Filled.Star, null, tint = Gold, modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(18.dp))
        }
        Text(d.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun DocRow(d: DocumentEntity, cover: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            EncryptedImage(cover, Modifier.size(width = 52.dp, height = 64.dp).clip(RoundedCornerShape(10.dp)), maxSide = 220)
            CategoryBadge(d.cat(), Modifier.align(Alignment.BottomEnd).padding(2.dp), size = 22)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(d.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                if (d.favorite) Icon(Icons.Filled.Star, null, tint = Gold, modifier = Modifier.padding(start = 4.dp).size(16.dp))
            }
            Text(
                listOf(d.cat().label, d.owner).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        ExpiryChip(d)
    }
}

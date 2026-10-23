package com.matan.vault.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.matan.vault.data.DocumentEntity
import java.time.LocalDate
import java.time.YearMonth

private val hebrewMonths = listOf(
    "ינואר", "פברואר", "מרץ", "אפריל", "מאי", "יוני",
    "יולי", "אוגוסט", "ספטמבר", "אוקטובר", "נובמבר", "דצמבר",
)

/** Everything that expires: overdue first, then the next 24 months grouped by month. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    documents: List<DocumentEntity>,
    covers: Map<Long, String>,
    onOpen: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val today = LocalDate.now()
    val dated = documents.filter { !it.archived && it.expiryDate != null }.sortedBy { it.expiryDate }
    val overdue = dated.filter { it.expiry()!!.isBefore(today) }
    val horizon = today.plusMonths(24)
    val upcoming = dated.filter { !it.expiry()!!.isBefore(today) && !it.expiry()!!.isAfter(horizon) }
        .groupBy { YearMonth.from(it.expiry()!!) }
    val later = dated.filter { it.expiry()!!.isAfter(horizon) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ציר תפוגות") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "חזרה") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 32.dp)) {
            if (dated.isEmpty()) {
                item {
                    Text(
                        "אין עדיין מסמכים עם תאריך תוקף.",
                        Modifier.fillMaxWidth().padding(32.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (overdue.isNotEmpty()) {
                item { SectionTitle("פג תוקף") }
                items(overdue, key = { it.id }) { d -> DocRow(d, covers[d.id]) { onOpen(d.id) } }
            }
            upcoming.forEach { (ym, list) ->
                item(key = ym.toString()) {
                    SectionTitle("${hebrewMonths[ym.monthValue - 1]} ${ym.year}")
                }
                items(list, key = { it.id }) { d -> DocRow(d, covers[d.id]) { onOpen(d.id) } }
            }
            if (later.isNotEmpty()) {
                item { SectionTitle("בהמשך") }
                items(later, key = { it.id }) { d -> DocRow(d, covers[d.id]) { onOpen(d.id) } }
            }
        }
    }
}

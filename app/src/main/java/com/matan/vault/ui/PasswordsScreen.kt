package com.matan.vault.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.widget.Toast
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import com.matan.vault.MainActivity
import com.matan.vault.PasswordAudit
import com.matan.vault.VaultViewModel
import com.matan.vault.data.CredentialEntity
import com.matan.vault.data.CredentialKind
import com.matan.vault.security.PasswordTools
import com.matan.vault.security.Strength
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// ---------- helpers ----------

fun CredentialKind.icon(): ImageVector = when (this) {
    CredentialKind.WEBSITE -> Icons.Outlined.Language
    CredentialKind.EMAIL -> Icons.Outlined.Email
    CredentialKind.BANK -> Icons.Outlined.AccountBalance
    CredentialKind.GOV -> Icons.Outlined.Gavel
    CredentialKind.WORK -> Icons.Outlined.Work
    CredentialKind.STUDIES -> Icons.Outlined.School
    CredentialKind.SOCIAL -> Icons.Outlined.Groups
    CredentialKind.SHOPPING -> Icons.Outlined.ShoppingCart
    CredentialKind.WIFI -> Icons.Outlined.Wifi
    CredentialKind.OTHER -> Icons.Outlined.Key
}

fun strengthColor(s: Strength): Color = when (s) {
    Strength.VERY_WEAK, Strength.WEAK -> Bad
    Strength.FAIR -> Warn
    Strength.STRONG, Strength.VERY_STRONG -> Good
}

private const val CLIP_CLEAR_MS = 30_000L

/** Copies a secret, hides it from the keyboard clipboard preview, and wipes the clipboard after 30 seconds. */
fun copySecret(context: Context, label: String, text: String) {
    if (text.isEmpty()) return
    val cm = context.getSystemService(ClipboardManager::class.java)
    val clip = ClipData.newPlainText(label, text)
    clip.description.extras = PersistableBundle().apply {
        if (Build.VERSION.SDK_INT >= 33) putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        else putBoolean("android.content.extra.IS_SENSITIVE", true)
    }
    cm.setPrimaryClip(clip)
    Handler(Looper.getMainLooper()).postDelayed({
        runCatching {
            if (Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip() else cm.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }, CLIP_CLEAR_MS)
    Toast.makeText(context, "$label הועתק · יימחק מהלוח בעוד 30 שניות", Toast.LENGTH_SHORT).show()
}

private fun copyPlain(context: Context, label: String, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "$label הועתק", Toast.LENGTH_SHORT).show()
}

private fun openUrl(context: Context, url: String) {
    val u = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) }
        .onFailure { Toast.makeText(context, "לא הצלחתי לפתוח את הכתובת", Toast.LENGTH_SHORT).show() }
}

private fun changedAgo(millis: Long): String {
    val d = ChronoUnit.DAYS.between(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate(), LocalDate.now())
    return when {
        d <= 0 -> "היום"
        d == 1L -> "אתמול"
        d < 60 -> "לפני $d ימים"
        d < 730 -> "לפני ${d / 30} חודשים"
        else -> "לפני ${d / 365} שנים"
    }
}

/** Left-to-right block for passwords, usernames and URLs inside the RTL app. */
@Composable
private fun Ltr(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, content = content)

@Composable
fun KindBadge(kind: CredentialKind, modifier: Modifier = Modifier, size: Int = 44) {
    Box(
        modifier.size(size.dp).clip(RoundedCornerShape((size / 3.5).dp)).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) { Icon(kind.icon(), null, Modifier.size((size * 0.52).dp), tint = MaterialTheme.colorScheme.primary) }
}

@Composable
private fun Pill(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

// ---------- list ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordsScreen(
    vm: VaultViewModel,
    credentials: List<CredentialEntity>,
    audit: PasswordAudit,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("ALL") }
    var onlyIssues by rememberSaveable { mutableStateOf(false) }
    val q = query.trim().lowercase()
    val usedKinds = CredentialKind.entries.filter { k -> credentials.any { it.kind == k.name } }
    val issues = audit.reused + audit.weak
    val visible = credentials.filter {
        it.matches(q) && (filter == "ALL" || it.kind == filter) && (!onlyIssues || it.id in issues)
    }
    val context = LocalContext.current
    val activity = context.findActivity() as? MainActivity
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("סיסמאות") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "חזרה") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Outlined.Password, null) },
                text = { Text("סיסמה חדשה") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 110.dp)) {
            if (credentials.isEmpty()) {
                item { PasswordsEmpty() }
                return@LazyColumn
            }
            item {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("חיפוש: בנק, ג׳ימייל, שם משתמש…") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (issues.isNotEmpty()) {
                item {
                    Card(
                        onClick = { onlyIssues = !onlyIssues },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Warn.copy(alpha = 0.10f)),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.WarningAmber, null, tint = Warn)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${issues.size} סיסמאות כדאי להחליף", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    listOfNotNull(
                                        audit.weak.size.takeIf { it > 0 }?.let { "$it חלשות" },
                                        audit.reused.size.takeIf { it > 0 }?.let { "$it בשימוש ביותר ממקום אחד" },
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(if (onlyIssues) "הצג הכול" else "הצג", color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            if (usedKinds.size > 1) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item { FilterChip(filter == "ALL", { filter = "ALL" }, { Text("הכול") }) }
                        items(usedKinds) { k ->
                            FilterChip(filter == k.name, { filter = if (filter == k.name) "ALL" else k.name }, { Text(k.label) },
                                leadingIcon = { Icon(k.icon(), null, Modifier.size(18.dp)) })
                        }
                    }
                }
            }
            items(visible, key = { it.id }) { c ->
                CredentialRow(
                    c,
                    reused = c.id in audit.reused,
                    weak = c.id in audit.weak,
                    onClick = { onOpen(c.id) },
                    onCopy = {
                        activity?.requireRecentAuth("העתקת סיסמה") {
                            scope.launch { copySecret(context, "הסיסמה", vm.reveal(c.secret)) }
                        }
                    },
                )
            }
            if (visible.isEmpty()) {
                item {
                    Text("לא נמצאו סיסמאות", Modifier.fillMaxWidth().padding(32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PasswordsEmpty() {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Password, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(20.dp))
        Text("אין עדיין סיסמאות", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "שמור כאן סיסמאות לאתרים, לבנק, למייל ול-Wi‑Fi. כל סיסמה מוצפנת במפתח נפרד שנשמר בשבב האבטחה של הטלפון, והצגה או העתקה דורשות טביעת אצבע.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun CredentialRow(c: CredentialEntity, reused: Boolean, weak: Boolean, onClick: () -> Unit, onCopy: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KindBadge(c.kindOf())
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                if (c.favorite) Icon(Icons.Filled.Star, null, tint = Gold, modifier = Modifier.padding(start = 4.dp).size(16.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    c.username.ifBlank { c.kindOf().label },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (weak) Pill("חלשה", Bad)
                if (reused) Pill("חוזרת", Warn)
            }
        }
        if (onCopy != null && c.secret.isNotEmpty()) {
            IconButton(onClick = onCopy) { Icon(Icons.Outlined.ContentCopy, "העתק סיסמה", tint = MaterialTheme.colorScheme.primary) }
        }
    }
}

// ---------- detail ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordDetailScreen(
    vm: VaultViewModel,
    id: Long,
    reused: Boolean,
    weak: Boolean,
    onEdit: () -> Unit,
    onBack: () -> Unit,
) {
    val cred by vm.observeCredential(id).collectAsState(initial = null)
    val context = LocalContext.current
    val activity = context.findActivity() as? MainActivity
    val scope = rememberCoroutineScope()
    var shownPw by remember { mutableStateOf<String?>(null) }
    var shownNotes by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val c = cred

    // whatever was revealed hides itself again after 30 seconds
    LaunchedEffect(shownPw) { if (shownPw != null) { delay(30_000); shownPw = null } }
    LaunchedEffect(shownNotes) { if (shownNotes != null) { delay(60_000); shownNotes = null } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(c?.title ?: "") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "חזרה") } },
                actions = {
                    if (c != null) {
                        IconButton(onClick = { vm.toggleFavorite(c) }) {
                            if (c.favorite) Icon(Icons.Filled.Star, "הסר ממועדפים", tint = Gold)
                            else Icon(Icons.Outlined.StarBorder, "הוסף למועדפים")
                        }
                        IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "עריכה") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "מחק") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        if (c == null) return@Scaffold
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                KindBadge(c.kindOf(), size = 56)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(c.kindOf().label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("הסיסמה עודכנה ${changedAgo(c.updatedAt)}", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (weak || reused) {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = (if (weak) Bad else Warn).copy(alpha = 0.09f)),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.WarningAmber, null, tint = if (weak) Bad else Warn)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            listOfNotNull(
                                "הסיסמה חלשה".takeIf { weak },
                                "אותה סיסמה שמורה גם במקום אחר".takeIf { reused },
                            ).joinToString(" · ") + ". כדאי להחליף אותה באתר ולעדכן כאן.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            ) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    if (c.username.isNotBlank()) {
                        FieldRow("שם משתמש", c.username) {
                            IconButton(onClick = { copyPlain(context, "שם המשתמש", c.username) }) { Icon(Icons.Outlined.ContentCopy, "העתק") }
                        }
                    }
                    if (c.secret.isNotEmpty()) {
                        FieldRow("סיסמה", shownPw ?: "••••••••••", mono = true) {
                            IconButton(onClick = {
                                if (shownPw != null) shownPw = null
                                else activity?.requireRecentAuth("הצגת סיסמה") { scope.launch { shownPw = vm.reveal(c.secret) } }
                            }) {
                                Icon(if (shownPw != null) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    if (shownPw != null) "הסתר" else "הצג")
                            }
                            IconButton(onClick = {
                                activity?.requireRecentAuth("העתקת סיסמה") {
                                    scope.launch { copySecret(context, "הסיסמה", shownPw ?: vm.reveal(c.secret)) }
                                }
                            }) { Icon(Icons.Outlined.ContentCopy, "העתק") }
                        }
                        shownPw?.let { pw ->
                            val st = PasswordTools.strength(pw)
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                LinearProgressIndicator(
                                    progress = { (st.score + 1) / 5f },
                                    color = strengthColor(st),
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(st.label, style = MaterialTheme.typography.labelMedium, color = strengthColor(st))
                            }
                        }
                    }
                    if (c.url.isNotBlank()) {
                        FieldRow("כתובת", c.url) {
                            IconButton(onClick = { openUrl(context, c.url) }) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, "פתח") }
                        }
                    }
                }
            }

            if (c.secureNotes.isNotEmpty()) {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                ) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("הערות מאובטחות", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text(shownNotes ?: "קוד, שאלות אבטחה וקודי שחזור — מוסתר", style = MaterialTheme.typography.bodyLarge,
                                color = if (shownNotes == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = {
                            if (shownNotes != null) shownNotes = null
                            else activity?.requireRecentAuth("הצגת הערות") { scope.launch { shownNotes = vm.reveal(c.secureNotes) } }
                        }) {
                            Icon(if (shownNotes != null) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null)
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete && c != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("למחוק את \"${c.title}\"?") },
            text = { Text("הסיסמה תימחק מהכספת לצמיתות.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete(c); onBack() }) { Text("מחק", color = Bad) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("ביטול") } },
        )
    }
}

@Composable
private fun FieldRow(label: String, value: String, mono: Boolean = false, actions: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Ltr {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyLarge.let { if (mono) it.copy(fontFamily = FontFamily.Monospace) else it },
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
        }
        actions()
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

// ---------- add / edit ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordEditScreen(
    vm: VaultViewModel,
    id: Long,
    onDone: (Long) -> Unit,
    onCancel: () -> Unit,
) {
    var loaded by remember { mutableStateOf(id == 0L) }
    var base by remember { mutableStateOf(CredentialEntity(title = "")) }
    var title by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(CredentialKind.WEBSITE) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var showPw by remember { mutableStateOf(id == 0L) }
    var genLength by remember { mutableIntStateOf(18) }
    var genSymbols by remember { mutableStateOf(true) }

    LaunchedEffect(id) {
        if (id != 0L) {
            vm.credential(id)?.let { c ->
                base = c; title = c.title; kind = c.kindOf(); username = c.username; url = c.url
                password = vm.reveal(c.secret); notes = vm.reveal(c.secureNotes)
            }
            loaded = true
        }
    }

    val st = PasswordTools.strength(password)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (id == 0L) "סיסמה חדשה" else "עריכת סיסמה") },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "ביטול") } },
                actions = {
                    TextButton(
                        enabled = loaded && (title.isNotBlank() || url.isNotBlank()) && password.isNotEmpty(),
                        onClick = {
                            vm.saveCredential(
                                base.copy(title = title, kind = kind.name, username = username, url = url),
                                password, notes, onDone,
                            )
                        },
                    ) { Text("שמור") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        if (!loaded) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CredentialKind.entries) { k ->
                    FilterChip(kind == k, { kind = k }, { Text(k.label) }, leadingIcon = { Icon(k.icon(), null, Modifier.size(18.dp)) })
                }
            }
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text(if (kind == CredentialKind.WIFI) "שם הרשת" else "שם (למשל: בנק הפועלים, Gmail)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            if (kind != CredentialKind.WIFI) {
                OutlinedTextField(
                    value = username, onValueChange = { username = it },
                    label = { Text("שם משתמש / מייל / ת״ז") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false),
                )
            }
            Ltr {
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("סיסמה") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    trailingIcon = {
                        IconButton(onClick = { showPw = !showPw }) {
                            Icon(if (showPw) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (showPw) "הסתר" else "הצג")
                        }
                    },
                )
            }
            if (password.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { (st.score + 1) / 5f },
                        color = strengthColor(st),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("חוזק: ${st.label}", style = MaterialTheme.typography.labelMedium, color = strengthColor(st))
                }
            }

            // generator
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("מחולל סיסמאות", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        FilledTonalButton(onClick = {
                            password = PasswordTools.generate(genLength, symbols = genSymbols); showPw = true
                        }) {
                            Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("צור")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("אורך", style = MaterialTheme.typography.labelLarge)
                        listOf(12, 16, 20, 24).forEach { n ->
                            FilterChip(genLength == n, { genLength = n }, { Text("$n") })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("כולל סימנים (!@#)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(genSymbols, { genSymbols = it })
                    }
                    Text(
                        "יש אתרים (בעיקר בנקים ואתרים ממשלתיים) שלא מקבלים סימנים — אם האתר מסרב, כבה את הסימנים וצור שוב.",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (kind != CredentialKind.WIFI) {
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("כתובת האתר (לא חובה)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                )
            }
            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text("הערות מאובטחות: קוד, שאלות אבטחה, קודי שחזור") },
                minLines = 3, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            )
            Text(
                "הסיסמה וההערות מוצפנות במפתח שנשמר בשבב האבטחה של הטלפון ולא יוצאות ממנו. אין גיבוי לענן — אם האפליקציה תימחק, גם הסיסמאות יימחקו.",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

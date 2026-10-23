package com.matan.vault.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.matan.vault.data.Category
import com.matan.vault.data.DocumentEntity
import com.matan.vault.data.ExpiryState
import com.matan.vault.security.FileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// ---------- formatting ----------

private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
fun LocalDate.fmt(): String = format(dateFmt)
fun Long.epochDayFmt(): String = LocalDate.ofEpochDay(this).fmt()

fun Category.icon(): ImageVector = when (this) {
    Category.ID -> Icons.Outlined.Badge
    Category.PASSPORT -> Icons.Outlined.Flight
    Category.LICENSE -> Icons.Outlined.Badge
    Category.VEHICLE -> Icons.Outlined.DirectionsCar
    Category.INSURANCE -> Icons.Outlined.Shield
    Category.FINANCE -> Icons.Outlined.AccountBalance
    Category.HOUSING -> Icons.Outlined.Home
    Category.RECEIPT -> Icons.Outlined.ReceiptLong
    Category.STUDIES -> Icons.Outlined.School
    Category.ARMY -> Icons.Outlined.MilitaryTech
    Category.HEALTH -> Icons.Outlined.LocalHospital
    Category.OTHER -> Icons.Outlined.Description
}

fun expiryColor(state: ExpiryState): Color = when (state) {
    ExpiryState.EXPIRED -> Bad
    ExpiryState.SOON -> Warn
    ExpiryState.OK -> Good
    ExpiryState.NONE -> Muted
}

fun expiryText(doc: DocumentEntity): String? {
    val left = doc.daysLeft() ?: return null
    return when {
        left < 0 -> "פג לפני ${-left} ימים"
        left == 0L -> "פג היום"
        left == 1L -> "פג מחר"
        left < 60 -> "עוד $left ימים"
        left < 730 -> "עוד ${left / 30} חודשים"
        else -> "בתוקף עד ${doc.expiry()!!.year}"
    }
}

@Composable
fun ExpiryChip(doc: DocumentEntity, modifier: Modifier = Modifier) {
    val text = expiryText(doc) ?: return
    val c = expiryColor(doc.expiryState())
    Surface(
        modifier = modifier,
        color = c.copy(alpha = 0.12f),
        contentColor = c,
        shape = RoundedCornerShape(50),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
fun CategoryBadge(category: Category, modifier: Modifier = Modifier, size: Int = 40) {
    Box(
        modifier
            .size(size.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(category.icon(), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size((size * 0.55).dp))
    }
}

// ---------- encrypted images ----------

object ThumbCache {
    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun load(context: Context, name: String, maxSide: Int): Bitmap? {
        val key = "$name@$maxSide"
        cache.get(key)?.let { return it }
        return try {
            val bytes = FileStore.read(context, name)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            bmp?.also { cache.put(key, it) }
        } catch (e: Exception) {
            null
        }
    }
}

@Composable
fun EncryptedImage(
    fileName: String?,
    modifier: Modifier = Modifier,
    maxSide: Int = 400,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val bmp by produceState<Bitmap?>(null, fileName, maxSide) {
        value = if (fileName == null) null else withContext(Dispatchers.IO) { ThumbCache.load(context, fileName, maxSide) }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) {
            Image(b.asImageBitmap(), null, contentScale = contentScale, modifier = Modifier.fillMaxSize())
        } else if (fileName != null) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Outlined.Description, null, tint = Muted)
        }
    }
}

// ---------- capture sources ----------

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

class Sources(val scan: () -> Unit, val pickFiles: () -> Unit)

/** Document scanner (edge detection, crop, multi-page) with a file picker fallback. */
@Composable
fun rememberSources(onUris: (List<Uri>) -> Unit): Sources {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onUris(uris)
    }
    val scanner = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val r = GmsDocumentScanningResult.fromActivityResultIntent(res.data)
            val uris = r?.pages?.map { it.imageUri }.orEmpty()
            if (uris.isNotEmpty()) onUris(uris)
        }
    }
    return remember(context) {
        Sources(
            scan = {
                val activity = context.findActivity()
                if (activity == null) {
                    picker.launch(arrayOf("image/*", "application/pdf"))
                } else {
                    val options = GmsDocumentScannerOptions.Builder()
                        .setGalleryImportAllowed(true)
                        .setPageLimit(20)
                        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                        .build()
                    GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                        .addOnSuccessListener { scanner.launch(IntentSenderRequest.Builder(it).build()) }
                        .addOnFailureListener { picker.launch(arrayOf("image/*", "application/pdf")) }
                }
            },
            pickFiles = { picker.launch(arrayOf("image/*", "application/pdf")) },
        )
    }
}

// ---------- date field ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    epochDay: Long?,
    onChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = epochDay?.epochDayFmt() ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null) },
            trailingIcon = if (epochDay != null) {
                { IconButton(onClick = { onChange(null) }) { Icon(Icons.Outlined.Close, "נקה") } }
            } else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // transparent overlay to open the picker (read-only text fields swallow clicks)
        Box(
            Modifier
                .matchParentSize()
                .padding(end = if (epochDay != null) 48.dp else 0.dp)
                .clickable { open = true }
        )
    }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (epochDay ?: LocalDate.now().toEpochDay()) * 86_400_000L
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay())
                    }
                    open = false
                }) { Text("אישור") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("ביטול") } },
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        action?.invoke()
    }
}

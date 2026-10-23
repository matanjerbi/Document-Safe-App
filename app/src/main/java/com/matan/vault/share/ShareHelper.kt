package com.matan.vault.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.matan.vault.data.DocumentEntity
import com.matan.vault.data.PageEntity
import com.matan.vault.security.FileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Sharing decrypts pages into a private cache folder that is exposed only through a FileProvider
 * grant, and that folder is wiped on every app start.
 */
object ShareHelper {

    fun sharedDir(context: Context) = File(context.cacheDir, "shared").apply { mkdirs() }

    fun clearShared(context: Context) {
        File(context.cacheDir, "shared").deleteRecursively()
    }

    suspend fun share(
        context: Context,
        doc: DocumentEntity,
        pages: List<PageEntity>,
        asPdf: Boolean,
        watermark: String?,
    ) {
        val uris = withContext(Dispatchers.IO) { prepare(context, doc, pages, asPdf, watermark) }
        if (uris.isEmpty()) return
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = if (asPdf) "application/pdf" else "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uris[0])
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/jpeg"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        }
        intent.putExtra(Intent.EXTRA_SUBJECT, doc.title)
        intent.clipData = ClipData.newRawUri(doc.title, uris[0]).apply {
            uris.drop(1).forEach { addItem(ClipData.Item(it)) }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(intent, "שיתוף ${doc.title}")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private fun prepare(
        context: Context,
        doc: DocumentEntity,
        pages: List<PageEntity>,
        asPdf: Boolean,
        watermark: String?,
    ): List<Uri> {
        clearShared(context)
        val dir = sharedDir(context)
        val base = doc.title.replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank { "document" }
        val bitmaps = pages.map { p ->
            val bytes = FileStore.read(context, p.fileName)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (watermark.isNullOrBlank()) bmp else watermarked(bmp, watermark)
        }
        val files = mutableListOf<File>()
        if (asPdf) {
            val pdf = PdfDocument()
            bitmaps.forEachIndexed { i, bmp ->
                val w = 595
                val h = (595f * bmp.height / bmp.width).toInt()
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(w, h, i + 1).create())
                page.canvas.drawBitmap(bmp, null, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
                pdf.finishPage(page)
            }
            val f = File(dir, "$base.pdf")
            f.outputStream().use { pdf.writeTo(it) }
            pdf.close()
            files += f
        } else {
            bitmaps.forEachIndexed { i, bmp ->
                val f = File(dir, if (bitmaps.size == 1) "$base.jpg" else "${base}_${i + 1}.jpg")
                f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                files += f
            }
        }
        val authority = context.packageName + ".files"
        return files.map { FileProvider.getUriForFile(context, authority, it) }
    }

    private fun watermarked(src: Bitmap, text: String): Bitmap {
        val bmp = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(bmp)
        val size = bmp.width / 22f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 200, 30, 30)
            textSize = size
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.save()
        canvas.rotate(-30f, bmp.width / 2f, bmp.height / 2f)
        var y = -bmp.height * 0.2f
        while (y < bmp.height * 1.2f) {
            canvas.drawText(text, bmp.width / 2f, y, paint)
            y += size * 4
        }
        canvas.restore()
        return bmp
    }
}

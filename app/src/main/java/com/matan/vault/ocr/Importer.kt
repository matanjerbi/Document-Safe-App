package com.matan.vault.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/** Turns incoming URIs (scanner pages, images, PDFs) into page bitmaps, and reads text from them on-device. */
object Importer {
    private const val MAX_SIDE = 2200

    suspend fun loadPages(context: Context, uris: List<Uri>): List<Bitmap> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Bitmap>()
        for (uri in uris) {
            val type = context.contentResolver.getType(uri) ?: ""
            if (type == "application/pdf" || uri.toString().endsWith(".pdf", ignoreCase = true)) {
                out += renderPdf(context, uri)
            } else {
                decodeImage(context, uri)?.let { out += it }
            }
        }
        out
    }

    private fun decodeImage(context: Context, uri: Uri): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= 28) {
            val src = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val w = info.size.width
                val h = info.size.height
                val scale = MAX_SIDE.toFloat() / maxOf(w, h)
                if (scale < 1f) decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }
    } catch (e: Exception) {
        null
    }

    private fun renderPdf(context: Context, uri: Uri): List<Bitmap> {
        val tmp = File(context.cacheDir, "import_${System.nanoTime()}.pdf")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            } ?: return emptyList()
            ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    (0 until minOf(renderer.pageCount, 30)).map { i ->
                        renderer.openPage(i).use { page ->
                            val scale = (MAX_SIDE.toFloat() / maxOf(page.width, page.height)).coerceAtMost(3f)
                            val bmp = Bitmap.createBitmap(
                                (page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888
                            )
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bmp
                        }
                    }
                }
            }
        } catch (e: Exception) {
            emptyList()
        } finally {
            tmp.delete()
        }
    }

    fun toJpeg(bitmap: Bitmap, quality: Int = 88): ByteArray =
        ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it); it.toByteArray() }

    /** On-device OCR. Latin script + digits: dates, numbers, MRZ and English labels. */
    suspend fun recognize(bitmaps: List<Bitmap>): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val parts = mutableListOf<String>()
            for (bmp in bitmaps.take(5)) {
                parts += try {
                    recognizer.process(InputImage.fromBitmap(bmp, 0)).await().text
                } catch (e: Exception) {
                    ""
                }
            }
            parts.joinToString("\n")
        } finally {
            recognizer.close()
        }
    }
}

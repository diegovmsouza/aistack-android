package br.com.amberwrite.aistack.feature.chat.composer

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.coroutines.coroutineContext

/** Nome, MIME e tamanho de um conteúdo escolhido (sem ler os bytes). */
data class AttachmentMeta(val name: String, val mime: String, val size: Long?)

/**
 * Leitura de anexos fora da main thread. Imagens são reduzidas (lado maior ≤ [MAX_SIDE]),
 * giradas conforme o EXIF e salvas em JPEG qualidade [JPEG_QUALITY]; demais arquivos vão crus,
 * com teto de [maxBytes] verificado durante a leitura (não carrega arquivos enormes na memória).
 */
class AttachmentReader(context: Context, private val maxBytes: Int) {
    private val app = context.applicationContext
    private val resolver: ContentResolver get() = app.contentResolver

    suspend fun meta(uri: Uri, fallbackName: String = "anexo"): AttachmentMeta = withContext(Dispatchers.IO) {
        var name: String? = null
        var size: Long? = null
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni)
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        }
        if (name == null && uri.scheme == ContentResolver.SCHEME_FILE) name = uri.lastPathSegment
        if (size == null && uri.scheme == ContentResolver.SCHEME_FILE) size = uri.path?.let { java.io.File(it).length() }
        val finalName = name?.takeIf { it.isNotBlank() } ?: fallbackName
        val mime = runCatching { resolver.getType(uri) }.getOrNull() ?: guessMime(finalName)
        AttachmentMeta(finalName, mime, size)
    }

    /** Lê o conteúdo pronto para o `saveUpload`. */
    suspend fun prepare(uri: Uri, meta: AttachmentMeta): PreparedUpload = withContext(Dispatchers.IO) {
        if (shouldCompressImage(meta.mime)) {
            compressImage(uri, meta)?.let { return@withContext it }
        }
        val bytes = readRaw(uri)
        PreparedUpload(bytes, meta.name, meta.mime)
    }

    private suspend fun readRaw(uri: Uri): ByteArray {
        val input = resolver.openInputStream(uri) ?: throw IOException("Não foi possível abrir o arquivo.")
        input.use { stream ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                coroutineContext.ensureActive()
                val n = stream.read(buf)
                if (n < 0) break
                total += n
                if (total > maxBytes) throw AttachmentTooLargeException(total)
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }

    /** `null` quando não dá para decodificar (o arquivo segue cru). */
    private suspend fun compressImage(uri: Uri, meta: AttachmentMeta): PreparedUpload? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        coroutineContext.ensureActive()

        val rotation = runCatching {
            resolver.openInputStream(uri)?.use {
                exifRotation(ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL))
            }
        }.getOrNull() ?: 0

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_SIDE)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = try {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (_: OutOfMemoryError) {
            null
        } ?: return null
        coroutineContext.ensureActive()

        val (w, h) = scaledSize(decoded.width, decoded.height, MAX_SIDE)
        val matrix = Matrix().apply {
            postScale(w.toFloat() / decoded.width, h.toFloat() / decoded.height)
            if (rotation != 0) postRotate(rotation.toFloat())
        }
        val transformed = if (matrix.isIdentity) decoded else
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        // JPEG não tem alfa: achata sobre branco para PNGs transparentes não ficarem pretos.
        val flat = if (transformed.hasAlpha()) {
            Bitmap.createBitmap(transformed.width, transformed.height, Bitmap.Config.ARGB_8888).also { out ->
                Canvas(out).apply {
                    drawColor(Color.WHITE)
                    drawBitmap(transformed, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
                }
            }
        } else transformed

        val out = ByteArrayOutputStream()
        flat.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        if (flat !== transformed) flat.recycle()
        if (transformed !== decoded) transformed.recycle()
        decoded.recycle()
        val bytes = out.toByteArray()
        if (bytes.size > maxBytes) throw AttachmentTooLargeException(bytes.size.toLong())
        return PreparedUpload(bytes, jpegName(meta.name), "image/jpeg")
    }

    companion object {
        const val MAX_SIDE = 2048
        const val JPEG_QUALITY = 85
    }
}

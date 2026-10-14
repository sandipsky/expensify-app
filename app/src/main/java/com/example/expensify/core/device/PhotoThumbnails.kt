package com.example.expensify.core.device

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Profile photos as the profile stores them (req §8): a small square JPEG as
 * a `data:image/jpeg;base64,…` URL, the port of ../expensify/src/lib/files/image.ts.
 */
@Singleton
class PhotoThumbnails @Inject constructor(@ApplicationContext private val context: Context) {

    /**
     * Crops the middle square out of the picked image and scales it down to
     * at most `edge` pixels a side, as a JPEG data URL small enough to store
     * on the profile itself. Null when the image can't be read.
     */
    suspend fun squareJpegDataUrl(uri: Uri, edge: Int, maxLength: Int): String? = withContext(Dispatchers.IO) {
        val source = decode(uri, edge) ?: return@withContext null
        val side = minOf(source.width, source.height)
        val size = minOf(edge, side)
        val cropped = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
        val scaled = if (cropped.width == size) cropped else Bitmap.createScaledBitmap(cropped, size, size, true)
        // JPEG has no transparency; white keeps a transparent PNG from turning black.
        val flat = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply {
            drawColor(Color.WHITE)
            drawBitmap(scaled, 0f, 0f, null)
        }
        // Lower quality until it fits the profile's limit; at 128 px even the first pass is a few KB.
        var quality = 85
        var url: String
        do {
            val bytes = ByteArrayOutputStream().also { flat.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
            url = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
            quality -= 15
        } while (url.length > maxLength && quality >= 30)
        url
    }

    /** The picked image, downsampled to roughly twice the edge so cropping and scaling stay sharp but cheap. */
    private fun decode(uri: Uri, edge: Int): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder applies the photo's EXIF rotation, which BitmapFactory ignores.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
                val sample = sampleSize(minOf(info.size.width, info.size.height), edge)
                if (sample > 1) decoder.setTargetSampleSize(sample)
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(minOf(bounds.outWidth, bounds.outHeight), edge) }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }
    } catch (_: Exception) {
        null
    }

    /** The largest power of two that keeps the shorter side at least twice `edge`. */
    private fun sampleSize(shorterSide: Int, edge: Int): Int {
        var sample = 1
        while (shorterSide / (sample * 2) >= edge * 2) sample *= 2
        return sample
    }

    companion object {
        /** The image a stored data URL holds, or null when it isn't one. */
        fun decodeDataUrl(dataUrl: String?): Bitmap? {
            if (dataUrl == null || !dataUrl.startsWith("data:image/")) return null
            val comma = dataUrl.indexOf(',')
            if (comma < 0) return null
            return try {
                val bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {
                null
            }
        }
    }
}

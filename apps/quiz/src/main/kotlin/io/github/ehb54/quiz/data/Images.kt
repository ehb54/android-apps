package io.github.ehb54.quiz.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Pictures are stored at most this many pixels on their longer side. */
private const val MAX_SIDE = 1600

/**
 * Stores a picture in [dir] for use on a card: scaled down, turned upright
 * (camera photos record their rotation separately), PNG kept as PNG for line
 * drawings and transparency, anything else saved as JPEG. Returns the new file
 * name, or null when [bytes] is not a picture Android can read.
 */
fun storeImage(dir: File, bytes: ByteArray): String? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
    val bitmap = upright(decoded, bytes)

    val png = bounds.outMimeType == "image/png"
    val name = UUID.randomUUID().toString() + if (png) ".png" else ".jpg"
    dir.mkdirs()
    FileOutputStream(File(dir, name)).use { out ->
        val format = if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        bitmap.compress(format, if (png) 100 else 90, out)
    }
    return name
}

private fun upright(bitmap: Bitmap, bytes: ByteArray): Bitmap {
    val orientation = runCatching {
        ExifInterface(ByteArrayInputStream(bytes))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val degrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> return bitmap
    }
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

/** Decodes a stored picture no larger than needed for [maxSide] pixels. */
fun loadImage(file: File, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
}

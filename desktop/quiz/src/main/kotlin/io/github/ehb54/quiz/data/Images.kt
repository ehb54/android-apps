package io.github.ehb54.quiz.data

import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.EncodedOrigin
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

// The desktop's version of the Android app's data/Images.kt (Skia in place of
// Android's BitmapFactory), with the same functions and the same results.

/** Pictures are stored at most this many pixels on their longer side. */
private const val MAX_SIDE = 1600

/**
 * Stores a picture in [dir] for use on a card: scaled down, turned upright
 * (camera photos record their rotation separately), PNG kept as PNG for line
 * drawings and transparency, anything else saved as JPEG. Returns the new file
 * name, or null when [bytes] is not a picture.
 */
fun storeImage(dir: File, bytes: ByteArray): String? {
    val image = runCatching { Image.makeFromEncoded(bytes) }.getOrNull() ?: return null
    val origin = runCatching { Codec.makeFromData(Data.makeFromBytes(bytes)).encodedOrigin }.getOrNull()
    val quarterTurns = when (origin) {
        EncodedOrigin.RIGHT_TOP -> 1
        EncodedOrigin.BOTTOM_RIGHT -> 2
        EncodedOrigin.LEFT_BOTTOM -> 3
        else -> 0
    }
    val png = isPng(bytes)
    val upright = draw(image, MAX_SIDE, quarterTurns, opaque = !png)
    val format = if (png) EncodedImageFormat.PNG else EncodedImageFormat.JPEG
    val data = upright.encodeToData(format, if (png) 100 else 90) ?: return null
    val name = UUID.randomUUID().toString() + if (png) ".png" else ".jpg"
    dir.mkdirs()
    File(dir, name).writeBytes(data.bytes)
    return name
}

/** Decodes a stored picture no larger than needed for [maxSide] pixels. */
fun loadImage(file: File, maxSide: Int): Image? {
    val image = runCatching { Image.makeFromEncoded(file.readBytes()) }.getOrNull() ?: return null
    return if (maxOf(image.width, image.height) <= maxSide) image else draw(image, maxSide, 0, opaque = false)
}

/** [image] scaled to fit [maxSide] and turned clockwise by [quarterTurns]; [opaque] paints white behind it (for JPEG). */
private fun draw(image: Image, maxSide: Int, quarterTurns: Int, opaque: Boolean): Image {
    val scale = minOf(1f, maxSide.toFloat() / maxOf(image.width, image.height))
    val width = (image.width * scale).roundToInt().coerceAtLeast(1)
    val height = (image.height * scale).roundToInt().coerceAtLeast(1)
    if (quarterTurns == 0 && scale == 1f) return image
    val sideways = quarterTurns % 2 == 1
    val surface = Surface.makeRasterN32Premul(if (sideways) height else width, if (sideways) width else height)
    val canvas = surface.canvas
    canvas.clear(if (opaque) 0xFFFFFFFF.toInt() else 0)
    when (quarterTurns) {
        1 -> canvas.translate(height.toFloat(), 0f).rotate(90f)
        2 -> canvas.translate(width.toFloat(), height.toFloat()).rotate(180f)
        3 -> canvas.translate(0f, width.toFloat()).rotate(270f)
    }
    canvas.drawImageRect(
        image,
        Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
        Rect.makeWH(width.toFloat(), height.toFloat()),
        SamplingMode.MITCHELL,
        null,
        true,
    )
    return surface.makeImageSnapshot()
}

private fun isPng(bytes: ByteArray) =
    bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() &&
        bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte()

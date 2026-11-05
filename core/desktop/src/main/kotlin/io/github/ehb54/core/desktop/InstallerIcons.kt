package io.github.ehb54.core.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.renderComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.EncodedImageFormat
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Writes the installers' icons into a folder: `<name>.icns` (macOS), `<name>.ico`
 * (Windows) and `<name>.png` (Linux), drawn from the app's launcher icon
 * ([Resources.launcherIcon]) as the window icon is. The desktop application
 * convention plugin runs this at build time (`generateInstallerIcons`).
 */
fun main(args: Array<String>) {
    require(args.size == 2) { "Usage: InstallerIcons <output folder> <name>" }
    writeInstallerIcons(Resources.launcherIcon(), File(args[0]), args[1])
}

fun writeInstallerIcons(icon: ImageVector, dir: File, name: String) {
    dir.mkdirs()
    // Apple's icon grid draws the tile at 824 of 1024 pixels, so macOS icons line up in the Dock.
    val mac = ICNS_TYPES.map { it.second }.distinct().associateWith { iconPng(icon, it, margin = 100f / 1024) }
    File(dir, "$name.icns").writeBytes(icns(mac))
    val windows = ICO_SIZES.associateWith { iconPng(icon, it, margin = 0f) }
    File(dir, "$name.ico").writeBytes(ico(windows))
    File(dir, "$name.png").writeBytes(iconPng(icon, LINUX_SIZE, margin = 1f / 16))
}

/** [icon] as a PNG [size] pixels square, with a transparent [margin] (a fraction of the size) round it. */
@OptIn(ExperimentalComposeUiApi::class)
internal fun iconPng(icon: ImageVector, size: Int, margin: Float): ByteArray {
    val inset = (size * margin).roundToInt()
    val image = renderComposeScene(size, size, Density(1f)) {
        Image(rememberVectorPainter(icon), null, Modifier.fillMaxSize().padding(inset.dp))
    }
    return image.use { checkNotNull(it.encodeToData(EncodedImageFormat.PNG)) { "PNG encoding failed" }.bytes }
}

/**
 * Apple's .icns: a header, then one entry per type, each holding a PNG. Several
 * types share a pixel size (32 is both 32 and 16 at 2x), so [pngs] is by size.
 */
internal fun icns(pngs: Map<Int, ByteArray>): ByteArray {
    val body = ByteArrayOutputStream()
    DataOutputStream(body).use { out ->
        for ((type, size) in ICNS_TYPES) {
            val png = pngs.getValue(size)
            out.writeBytes(type)
            out.writeInt(8 + png.size)
            out.write(png)
        }
    }
    val file = ByteArrayOutputStream()
    DataOutputStream(file).use { out ->
        out.writeBytes("icns")
        out.writeInt(8 + body.size())
        body.writeTo(out)
    }
    return file.toByteArray()
}

/** Windows' .ico: a header and a directory of entries (little-endian), then the PNGs. */
internal fun ico(pngs: Map<Int, ByteArray>): ByteArray {
    val header = 6 + 16 * pngs.size
    val buffer = ByteBuffer.allocate(header + pngs.values.sumOf { it.size }).order(ByteOrder.LITTLE_ENDIAN)
    buffer.putShort(0).putShort(1).putShort(pngs.size.toShort())
    var offset = header
    for ((size, png) in pngs) {
        // Width and height are a byte each, with 0 meaning 256.
        buffer.put((size % 256).toByte()).put((size % 256).toByte())
        buffer.put(0.toByte()).put(0.toByte()) // no palette; reserved
        buffer.putShort(1).putShort(32) // planes, bits per pixel
        buffer.putInt(png.size).putInt(offset)
        offset += png.size
    }
    pngs.values.forEach { buffer.put(it) }
    return buffer.array()
}

/**
 * The .icns entry types that hold PNGs, with their pixel sizes (the @2x types
 * repeat a size): what Apple's iconutil makes from an iconset. (Not icp6, which
 * macOS reads as 48 pixels.)
 */
internal val ICNS_TYPES = listOf(
    "icp4" to 16, "icp5" to 32, "ic07" to 128, "ic08" to 256, "ic09" to 512,
    "ic11" to 32, "ic12" to 64, "ic13" to 256, "ic14" to 512, "ic10" to 1024,
)

/** The sizes Windows picks from for the taskbar, Start menu, desktop and Explorer. */
internal val ICO_SIZES = listOf(16, 24, 32, 48, 64, 128, 256)

private const val LINUX_SIZE = 512

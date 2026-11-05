package io.github.ehb54.core.desktop

import androidx.compose.ui.graphics.Color
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class InstallerIconsTest {
    @get:Rule
    val folder = TemporaryFolder()

    /** An adaptive-icon foreground: a white square in the middle of the 108 dp drawable. */
    private val icon = loadVector(
        """
        <vector xmlns:android="http://schemas.android.com/apk/res/android"
            android:width="108dp" android:height="108dp"
            android:viewportWidth="108" android:viewportHeight="108">
            <path android:pathData="M44,44h20v20h-20z" android:fillColor="#FFFFFF" />
        </vector>
        """.trimIndent().byteInputStream(),
        background = Color.Red,
    )

    @Test
    fun launcherIconShowsTheMiddleTwoThirdsOnARoundedSquare() {
        val pixels = pixels(iconPng(icon, 72, margin = 0f))
        assertEquals(0, alpha(pixels.getColor(0, 0))) // a rounded corner
        assertEquals(RED, pixels.getColor(2, 36)) // the tile reaches the edge
        // The 20 dp square sits in the middle at the same scale: 72 px for 72 dp.
        assertEquals(WHITE, pixels.getColor(36, 36))
        assertEquals(WHITE, pixels.getColor(27, 27))
        assertEquals(RED, pixels.getColor(24, 24))
    }

    @Test
    fun macIconsKeepApplesMargin() {
        val pixels = pixels(iconPng(icon, 1024, margin = 100f / 1024))
        assertEquals(0, alpha(pixels.getColor(50, 512)))
        assertEquals(RED, pixels.getColor(150, 512))
    }

    @Test
    fun icnsHoldsAPngForEachType() {
        writeInstallerIcons(icon, folder.root, "app")
        val bytes = File(folder.root, "app.icns").readBytes()
        val data = ByteBuffer.wrap(bytes) // big-endian
        assertEquals("icns", type(data))
        assertEquals(bytes.size, data.int)
        for ((expectedType, size) in ICNS_TYPES) {
            assertEquals(expectedType, type(data))
            val png = ByteArray(data.int - 8).also { data.get(it) }
            assertEquals(size, Image.makeFromEncoded(png).width)
        }
        assertEquals(0, data.remaining())
    }

    @Test
    fun icoHoldsAPngForEachSize() {
        writeInstallerIcons(icon, folder.root, "app")
        val bytes = File(folder.root, "app.ico").readBytes()
        val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0, data.short.toInt())
        assertEquals(1, data.short.toInt()) // an icon, not a cursor
        assertEquals(ICO_SIZES.size, data.short.toInt())
        for (size in ICO_SIZES) {
            assertEquals(size % 256, data.get().toInt() and 0xff)
            assertEquals(size % 256, data.get().toInt() and 0xff)
            data.position(data.position() + 6)
            val length = data.int
            val offset = data.int
            val png = bytes.copyOfRange(offset, offset + length)
            assertArrayEquals(PNG_SIGNATURE, png.copyOf(8))
            assertEquals(size, Image.makeFromEncoded(png).width)
        }
        val linux = Image.makeFromEncoded(File(folder.root, "app.png").readBytes())
        assertEquals(512, linux.width)
    }

    private fun pixels(png: ByteArray) = Bitmap.makeFromImage(Image.makeFromEncoded(png))

    private fun type(data: ByteBuffer) = String(ByteArray(4).also { data.get(it) }, Charsets.US_ASCII)

    private fun alpha(color: Int) = color ushr 24

    private companion object {
        const val RED = 0xffff0000.toInt()
        const val WHITE = 0xffffffff.toInt()
        val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)
    }
}

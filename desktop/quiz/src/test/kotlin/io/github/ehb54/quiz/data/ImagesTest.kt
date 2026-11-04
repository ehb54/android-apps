package io.github.ehb54.quiz.data

import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ImagesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun png(width: Int, height: Int): ByteArray {
        val surface = Surface.makeRasterN32Premul(width, height)
        surface.canvas.clear(0xFF336699.toInt())
        return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
    }

    @Test
    fun bigPicturesAreStoredScaledDownAndPngStaysPng() {
        val name = storeImage(folder.root, png(3200, 1000))
        assertNotNull(name)
        assertTrue(name!!.endsWith(".png"))
        val stored = loadImage(File(folder.root, name), 4000)!!
        assertEquals(1600, stored.width)
        assertEquals(500, stored.height)
        // Loading for a small view scales down further.
        assertEquals(400, loadImage(File(folder.root, name), 400)!!.width)
    }

    @Test
    fun somethingThatIsNotAPictureIsRefused() {
        assertNull(storeImage(folder.root, "not a picture".toByteArray()))
    }
}

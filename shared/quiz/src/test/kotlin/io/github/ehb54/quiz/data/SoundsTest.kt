package io.github.ehb54.quiz.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SoundsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun ascii(text: String, padTo: Int = 16) = text.toByteArray().copyOf(maxOf(padTo, text.length))

    @Test
    fun recognizesSoundFilesByTheirFirstBytes() {
        val wav = ascii("RIFF").copyOf(12).also { "WAVE".toByteArray().copyInto(it, 8) }
        assertEquals("wav", soundExtension(wav))
        assertEquals("ogg", soundExtension(ascii("OggS")))
        assertEquals("flac", soundExtension(ascii("fLaC")))
        assertEquals("m4a", soundExtension(ByteArray(4) + ascii("ftypM4A ")))
        assertEquals("mp3", soundExtension(ascii("ID3")))
        // Bare frames: MPEG-1 layer III (FF FB), and AAC in ADTS (FF F1).
        assertEquals("mp3", soundExtension(bytes(0xff, 0xfb, 0x90, 0x00)))
        assertEquals("aac", soundExtension(bytes(0xff, 0xf1, 0x50, 0x80)))
    }

    @Test
    fun anythingElseIsNotASound() {
        assertNull(soundExtension(ByteArray(0)))
        assertNull(soundExtension(ascii("RIFF")))
        assertNull(soundExtension(bytes(0x89, 'P'.code, 'N'.code, 'G'.code)))
        assertNull(soundExtension("cat\nchat\n".toByteArray()))
        assertNull(soundExtension(bytes(0xff, 0xd8, 0xff, 0xe0)))
    }

    @Test
    fun storesASoundAsItIsUnderItsKind() {
        val dir = File(folder.root, "media")
        val ogg = ascii("OggS", padTo = 64)
        val name = storeSound(dir, ogg)!!
        assertEquals("ogg", name.substringAfterLast('.'))
        assertArrayEquals(ogg, File(dir, name).readBytes())
        assertNull(storeSound(dir, ascii("not a sound")))
        assertEquals(listOf(name), dir.list()!!.toList())
    }
}

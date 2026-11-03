package io.github.ehb54.quiz.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream

class QuizFilesTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun parsesOriginalExportsWithDosLineEndingsAndStrayLines() {
        val text = "\uFEFFcat\r\nchat\r\ndog\r\nchien\r\n\r\n\r\nodd line\n"
        assertEquals(listOf(QzxCard("cat", "chat"), QzxCard("dog", "chien")), parseQzx(text))
    }

    @Test
    fun formatWritesOneLinePerSide() {
        val cards = listOf(QzxCard("a", "b"), QzxCard("two\nlines", "c"))
        assertEquals("a\nb\ntwo lines\nc\n", formatQzx(cards))
        assertEquals(listOf(QzxCard("a", "b"), QzxCard("two lines", "c")), parseQzx(formatQzx(cards)))
    }

    @Test
    fun zipRoundTripKeepsQzxAndPictures() {
        val picture = folder.newFile("mol.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val out = ByteArrayOutputStream()
        writeZip(out, "molecules", "!mol.png\ncaffeine\n", mapOf("mol.png" to picture))
        val bytes = out.toByteArray()
        assertTrue(isZip(bytes))
        assertFalse(isZip("cat\nchat\n".toByteArray()))

        val bundle = readZip(bytes.inputStream())
        assertEquals("!mol.png\ncaffeine\n", bundle.qzx)
        assertArrayEquals(byteArrayOf(1, 2, 3), bundle.images.getValue("mol.png"))
    }

    @Test(expected = java.io.IOException::class)
    fun zipLargerThanTheLimitIsRefused() {
        val picture = folder.newFile("big.png").apply { writeBytes(ByteArray(10_000)) }
        val out = ByteArrayOutputStream()
        writeZip(out, "big", "!big.png\nx\n", mapOf("big.png" to picture))
        readZip(out.toByteArray().inputStream(), maxBytes = 5_000)
    }

    @Test
    fun forgivingMatchIgnoresCaseSpacesAndUnicodeForm() {
        assertTrue(answersMatch("  Café   au lait ", "café AU LAIT"))
        assertFalse(answersMatch("cafe", "café"))
    }
}

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
    fun zipRoundTripKeepsQzxPicturesAndSounds() {
        val picture = folder.newFile("mol.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val sound = folder.newFile("say.mp3").apply { writeBytes(byteArrayOf(4, 5)) }
        val notes = folder.newFile("notes.txt").apply { writeBytes(byteArrayOf(6)) }
        val out = ByteArrayOutputStream()
        val qzx = "!mol.png\ncaffeine\n!say.mp3\ncaffeine\n"
        writeZip(out, "molecules", qzx, mapOf("mol.png" to picture, "sounds/say.mp3" to sound, "notes.txt" to notes))
        val bytes = out.toByteArray()
        assertTrue(isZip(bytes))
        assertFalse(isZip("cat\nchat\n".toByteArray()))

        // Pictures and sounds are kept by file name, without folders; other files are left out.
        val bundle = readZip(bytes.inputStream())
        assertEquals(qzx, bundle.qzx)
        assertEquals(setOf("mol.png", "say.mp3"), bundle.files.keys)
        assertArrayEquals(byteArrayOf(1, 2, 3), bundle.files.getValue("mol.png"))
        assertArrayEquals(byteArrayOf(4, 5), bundle.files.getValue("say.mp3"))
    }

    @Test
    fun sidesCarryWhatShowsAfterTheAnswer() {
        val files = setOf("hand.webp", "hand.mp3", "my dog.png", "dog.mp3")
        // A file alone, a file with its text, text with a sound, a picture with both.
        assertEquals(QzxSide("", "hand.webp"), parseSide("!hand.webp", files))
        assertEquals(QzxSide("die Hand", "hand.mp3"), parseSide("!hand.mp3 die Hand", files))
        assertEquals(QzxSide("die Hand", sound = "hand.mp3"), parseSide("die Hand !hand.mp3", files))
        assertEquals(QzxSide("die Hand", "hand.webp", "hand.mp3"), parseSide("!hand.webp die Hand !hand.mp3", files))
        assertEquals(QzxSide("", "hand.webp", "hand.mp3"), parseSide("!hand.webp !hand.mp3", files))
        // Names may hold spaces; a name the zip lacks, or a picture at the end, stays text.
        assertEquals(QzxSide("der Hund", "my dog.png", "dog.mp3"), parseSide("!my dog.png der Hund !dog.mp3", files))
        assertEquals(QzxSide("!cat.png"), parseSide("!cat.png", files))
        assertEquals(QzxSide("Hi !there"), parseSide("Hi !there", files))
        assertEquals(QzxSide("see !hand.webp"), parseSide("see !hand.webp", files))
        // A sound side needs no sound for after the answer: a second one stays its text.
        assertEquals(QzxSide("die Hand !dog.mp3", "hand.mp3"), parseSide("!hand.mp3 die Hand !dog.mp3", files))

        val sides = listOf("!hand.webp", "!hand.mp3 die Hand", "die Hand !hand.mp3", "!my dog.png der Hund !dog.mp3")
        for (side in sides) assertEquals(side, formatSide(parseSide(side, files)))
        assertEquals("plain text", formatSide(QzxSide("plain text")))
        assertEquals("!hand.mp3", formatSide(QzxSide("", "hand.mp3", "dog.mp3")))
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

    @Test
    fun germanLettersMayBeTypedWithoutAGermanKeyboard() {
        assertTrue(answersMatch("das maedchen", "das Mädchen"))
        assertTrue(answersMatch("die Fuesse", "die Füße"))
        assertTrue(answersMatch("die Strasse", "die Straße"))
        assertTrue(answersMatch("DIE STRAẞE", "die strasse"))
        assertTrue(answersMatch("der koenig", "der König"))
        assertTrue(answersMatch("the king’s son", "the king's son"))
        assertFalse(answersMatch("das madchen", "das Mädchen"))
    }
}

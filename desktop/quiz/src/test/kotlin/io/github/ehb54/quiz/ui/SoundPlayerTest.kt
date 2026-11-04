package io.github.ehb54.quiz.ui

import io.github.ehb54.quiz.engine.readZip
import io.github.ehb54.quiz.model.isSound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

class SoundPlayerTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun theMorseSampleIsInAFormatJavaSoundPlays() {
        val zip = File("../../apps/quiz/src/main/assets/samples/Morse code.zip")
        val sounds = zip.inputStream().use { readZip(it) }.files.filterKeys { isSound(it) }
        assertEquals(26, sounds.size)
        for ((name, bytes) in sounds) {
            AudioSystem.getAudioInputStream(ByteArrayInputStream(bytes)).use { stream ->
                assertEquals(name, AudioFormat.Encoding.PCM_SIGNED, stream.format.encoding)
                assertTrue(name, stream.frameLength > 0)
            }
        }
    }

    @Test
    fun aSoundItCannotPlayEndsWithAnError() {
        // An Ogg header, as a stored sound would have, but nothing Java Sound reads.
        val file = File(folder.root, "x.ogg").apply { writeBytes("OggS".toByteArray() + ByteArray(60)) }
        var errors = 0
        var done = 0
        val player = SoundPlayer { errors++ }
        player.play(file) { done++ }
        assertEquals(listOf(1, 1), listOf(errors, done))
        assertNull(player.playing)
        // Stopping afterwards does nothing more.
        player.stop()
        assertEquals(listOf(1, 1), listOf(errors, done))
    }
}

package io.github.ehb54.quiz.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.JavaLayerException
import javazoom.jl.decoder.SampleBuffer
import java.awt.EventQueue
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.LineEvent
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.UnsupportedAudioFileException

// The desktop's version of the Android app's ui/SoundPlayer.kt, with the same
// API. It plays through Java Sound, which is part of the Java runtime but reads
// only uncompressed sound (WAV, AIFF, AU), and MP3 through JLayer (see
// openSound). AAC, Ogg Vorbis and FLAC are still stored and exported, and play
// on the phone; here they report an error.

/**
 * [file] as sound Java Sound plays: WAV, AIFF and AU as they are; an MP3
 * decoded to 16-bit PCM by JLayer (javazoom, LGPL 2.1, shipped as its own jar),
 * a pure-Java decoder of every MPEG audio rate. Its output matches ffmpeg's
 * within 48 dB on the samples' sounds. (javamp3, MIT, was tried first: its
 * decodeFullyInto stops after one frame, and read through, it decoded 718 of
 * the 1,050 sample sounds with stretches of noise.)
 */
internal fun openSound(file: File): AudioInputStream =
    if (file.extension.lowercase() == "mp3") decodeMp3(file) else AudioSystem.getAudioInputStream(file)

private fun decodeMp3(file: File): AudioInputStream {
    val stream = Bitstream(ByteArrayInputStream(file.readBytes()))
    val decoder = Decoder()
    val pcm = ByteArrayOutputStream()
    var format: AudioFormat? = null
    try {
        while (true) {
            val header = stream.readFrame() ?: break
            val decoded = decoder.decodeFrame(header, stream) as SampleBuffer
            format = format ?: AudioFormat(decoded.sampleFrequency.toFloat(), 16, decoded.channelCount, true, false)
            // Interleaved 16-bit samples, written little-endian.
            for (i in 0 until decoded.bufferLength) {
                val sample = decoded.buffer[i].toInt()
                pcm.write(sample and 0xff)
                pcm.write(sample shr 8 and 0xff)
            }
            stream.closeFrame()
        }
    } catch (e: JavaLayerException) {
        throw IOException("can't decode $file", e)
    } catch (e: RuntimeException) {
        // JLayer on damaged MP3 data.
        throw IOException("can't decode $file", e)
    } finally {
        stream.close()
    }
    val found = format ?: throw UnsupportedAudioFileException("no MP3 frames in $file")
    val data = pcm.toByteArray()
    return AudioInputStream(ByteArrayInputStream(data), found, data.size.toLong() / found.frameSize)
}

/** Plays card sounds, one at a time: a new sound stops the one playing. */
class SoundPlayer(private val onError: () -> Unit) {
    private var clip: Clip? = null
    private var onDone: (() -> Unit)? = null

    /** The sound playing now, or null; its button shows a stop sign. */
    var playing by mutableStateOf<File?>(null)
        private set

    /** Plays [file]. [onDone] runs when it ends, is stopped or can't be played. */
    fun play(file: File, onDone: () -> Unit = {}) {
        stop()
        this.onDone = onDone
        playing = file
        val opened = try {
            openSound(file).use { stream ->
                AudioSystem.getClip().apply { open(stream) }
            }
        } catch (e: UnsupportedAudioFileException) {
            null
        } catch (e: LineUnavailableException) {
            null
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) {
            // No audio device that can play it.
            null
        } catch (e: SecurityException) {
            null
        }
        if (opened == null) {
            fail()
            return
        }
        clip = opened
        // Java Sound reports on its own thread: hand the end to the UI thread,
        // where only the current clip counts (a stopped one can still report).
        opened.addLineListener { event ->
            if (event.type == LineEvent.Type.STOP) EventQueue.invokeLater { if (clip === opened) stop() }
        }
        opened.start()
    }

    /** Stops the sound playing, if any; its onDone runs. */
    fun stop() {
        val done = onDone
        onDone = null
        clip?.close()
        clip = null
        playing = null
        done?.invoke()
    }

    private fun fail() {
        stop()
        onError()
    }
}

/** The app's [SoundPlayer], so every screen plays through the same one. */
val LocalSoundPlayer = staticCompositionLocalOf<SoundPlayer> { error("no SoundPlayer provided") }

/**
 * A [SoundPlayer] for as long as this composition lasts. [onError] runs when a
 * sound can't be played.
 */
@Composable
fun rememberSoundPlayer(onError: () -> Unit): SoundPlayer {
    val latestOnError by rememberUpdatedState(onError)
    val player = remember { SoundPlayer { latestOnError() } }
    DisposableEffect(player) { onDispose { player.stop() } }
    return player
}

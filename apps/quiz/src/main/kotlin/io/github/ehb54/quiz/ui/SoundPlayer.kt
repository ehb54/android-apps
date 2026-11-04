package io.github.ehb54.quiz.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import java.io.File
import java.io.IOException

/**
 * Plays card sounds, one at a time: a new sound stops the one playing. Clips
 * are short, so other apps' audio is asked to duck rather than stop. This is
 * the only file that uses Android's media classes; a desktop build supplies
 * its own with the same API.
 */
class SoundPlayer(context: Context, private val onError: () -> Unit) {
    private val audio: AudioManager? = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()
    private var player: MediaPlayer? = null
    private var onDone: (() -> Unit)? = null

    /** The sound playing now, or null; its button shows a stop sign. */
    var playing by mutableStateOf<File?>(null)
        private set

    /** Plays [file]. [onDone] runs when it ends, is stopped or can't be played. */
    fun play(file: File, onDone: () -> Unit = {}) {
        stop()
        this.onDone = onDone
        playing = file
        audio?.requestAudioFocus(focus)
        val media = MediaPlayer()
        player = media
        // A player stopped early can still report; only the current one counts.
        media.setOnPreparedListener { if (player === media) media.start() }
        media.setOnCompletionListener { if (player === media) stop() }
        media.setOnErrorListener { _, _, _ ->
            if (player === media) fail()
            true
        }
        try {
            media.setAudioAttributes(attributes)
            media.setDataSource(file.path)
            media.prepareAsync()
        } catch (e: IOException) {
            fail()
        } catch (e: IllegalArgumentException) {
            fail()
        } catch (e: IllegalStateException) {
            fail()
        } catch (e: SecurityException) {
            fail()
        }
    }

    /** Stops the sound playing, if any; its onDone runs. */
    fun stop() {
        val done = onDone
        onDone = null
        player?.release()
        player = null
        if (playing != null) audio?.abandonAudioFocusRequest(focus)
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
 * A [SoundPlayer] for as long as this composition lasts. It stops when the app
 * goes to the background. [onError] runs when a sound can't be played.
 */
@Composable
fun rememberSoundPlayer(onError: () -> Unit): SoundPlayer {
    val context = LocalContext.current.applicationContext
    val latestOnError by rememberUpdatedState(onError)
    val player = remember(context) { SoundPlayer(context) { latestOnError() } }
    DisposableEffect(player) { onDispose { player.stop() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.stop() }
    return player
}

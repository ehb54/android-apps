package io.github.ehb54.core.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * The desktop's Back: the Escape key (and the mouse's back button, where the
 * app forwards it). The handler registered last gets it, as with Android's
 * BackHandler.
 */
class BackDispatcher {
    private val handlers = mutableListOf<() -> Unit>()

    internal fun add(handler: () -> Unit) {
        handlers += handler
    }

    internal fun remove(handler: () -> Unit) {
        handlers.remove(handler)
    }

    /** Runs the newest handler; false when there is none. */
    fun back(): Boolean {
        val handler = handlers.lastOrNull() ?: return false
        handler()
        return true
    }

    /** For a window's onKeyEvent: Escape means Back. */
    fun onKeyEvent(event: KeyEvent): Boolean =
        event.type == KeyEventType.KeyDown && event.key == Key.Escape && back()
}

val LocalBackDispatcher = staticCompositionLocalOf { BackDispatcher() }

/** Android's BackHandler, for the desktop: [onBack] runs on Escape while this is on screen and [enabled]. */
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val current = rememberUpdatedState(onBack)
    val dispatcher = LocalBackDispatcher.current
    DisposableEffect(dispatcher, enabled) {
        val handler = { current.value() }
        if (enabled) dispatcher.add(handler)
        onDispose { dispatcher.remove(handler) }
    }
}

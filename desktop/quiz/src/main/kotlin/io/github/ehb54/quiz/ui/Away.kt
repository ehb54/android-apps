package io.github.ehb54.quiz.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalWindowInfo

/**
 * The desktop's own version of the Android app's `ui/Away.kt`: calls [onAway]
 * when the window loses focus (another window in front, or minimized). Only a
 * change counts, so a window that has not had focus yet is not away.
 */
@Composable
fun WhenAway(onAway: () -> Unit) {
    val focused = LocalWindowInfo.current.isWindowFocused
    val away by rememberUpdatedState(onAway)
    var wasFocused by remember { mutableStateOf(focused) }
    LaunchedEffect(focused) {
        if (wasFocused && !focused) away()
        wasFocused = focused
    }
}

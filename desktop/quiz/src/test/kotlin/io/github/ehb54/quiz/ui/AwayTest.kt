package io.github.ehb54.quiz.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** On the desktop, a session is away when its window loses focus. */
class AwayTest {
    @get:Rule
    val compose = createComposeRule()

    private class Window(focused: Boolean) : WindowInfo {
        override var isWindowFocused by mutableStateOf(focused)
    }

    @Test
    fun losingFocusIsAwayButNotHavingHadItIsNot() {
        val window = Window(focused = false)
        var away = 0
        compose.setContent {
            CompositionLocalProvider(LocalWindowInfo provides window) { WhenAway { away++ } }
        }
        compose.waitForIdle()
        assertEquals(0, away)

        window.isWindowFocused = true
        compose.waitForIdle()
        assertEquals(0, away)

        window.isWindowFocused = false
        compose.waitForIdle()
        assertEquals(1, away)

        window.isWindowFocused = true
        compose.waitForIdle()
        window.isWindowFocused = false
        compose.waitForIdle()
        assertEquals(2, away)
    }
}

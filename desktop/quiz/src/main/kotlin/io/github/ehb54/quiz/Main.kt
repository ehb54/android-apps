package io.github.ehb54.quiz

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.ehb54.core.desktop.AppTheme
import io.github.ehb54.core.desktop.Application
import io.github.ehb54.core.desktop.BackDispatcher
import io.github.ehb54.core.desktop.BuildInfo
import io.github.ehb54.core.desktop.LocalAppWindow
import io.github.ehb54.core.desktop.LocalBackDispatcher
import io.github.ehb54.core.desktop.Resources
import io.github.ehb54.core.desktop.stringResource
import io.github.ehb54.core.desktop.waitForIdle
import io.github.ehb54.quiz.ui.QuizApp

/**
 * Quiz on the desktop: one window, phone-shaped to start with, showing the
 * Android app's screens. Escape is Back. Quizzes live in the system's
 * application data folder (see core/desktop's AppFolders).
 */
fun main() {
    Application("Quiz")
    application {
        val back = remember { BackDispatcher() }
        val icon = rememberVectorPainter(
            remember { Resources.launcherIcon(R.drawable.ic_launcher_foreground, R.color.ic_launcher_background) },
        )
        Window(
            onCloseRequest = ::exitApplication,
            state = rememberWindowState(size = DpSize(480.dp, 820.dp)),
            title = stringResource(R.string.app_name),
            icon = icon,
            onKeyEvent = back::onKeyEvent,
        ) {
            CompositionLocalProvider(LocalBackDispatcher provides back, LocalAppWindow provides window) {
                AppTheme {
                    QuizApp(BuildInfo(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.BUILD_TIME))
                }
            }
        }
    }
    // Saves run on a background thread; let the last one finish before the app exits.
    waitForIdle("quiz-disk")
}

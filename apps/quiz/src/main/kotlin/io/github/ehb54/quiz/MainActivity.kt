package io.github.ehb54.quiz

import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.ehb54.core.ui.AppTheme
import io.github.ehb54.core.ui.BuildInfo
import io.github.ehb54.quiz.ui.QuizApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // The volume keys set the volume of card sounds, even between sounds.
        volumeControlStream = AudioManager.STREAM_MUSIC
        val info = BuildInfo(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.BUILD_TIME)
        setContent {
            AppTheme {
                QuizApp(info)
            }
        }
    }
}

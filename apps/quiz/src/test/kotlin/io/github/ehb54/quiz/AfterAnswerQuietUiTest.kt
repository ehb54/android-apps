package io.github.ehb54.quiz

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import io.github.ehb54.quiz.model.QuizMode
import io.github.ehb54.quiz.model.QuizSettings
import io.github.ehb54.quiz.ui.choiceTag
import org.junit.Test
import org.robolectric.shadows.ShadowMediaPlayer

/**
 * "Play the sound after the answer" starts off: the whole card shows after the
 * answer without a sound, and its button still plays it.
 */
class AfterAnswerQuietUiTest : QuizUiTest() {
    init {
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(60_000, 0) }
    }

    @Test
    fun theSoundWaitsForItsButton() {
        saveListeningQuiz(QuizSettings(mode = QuizMode.Pick, shuffle = false, choices = 3))

        ActivityScenario.launch(MainActivity::class.java).use {
            startQuiz("Listening", 3)
            // The asked sound plays by itself, as ever; stopped, so what follows is the answer's.
            compose.onNodeWithContentDescription("Stop sound").performClick()
            compose.onNodeWithTag(choiceTag(1)).performClick()
            compose.waitForText("Right.")
            compose.onNodeWithText("die Hand").assertExists()
            compose.onNodeWithContentDescription("Stop sound").assertDoesNotExist()
            compose.onNodeWithContentDescription("Play sound").performClick()
            compose.onNodeWithContentDescription("Stop sound").assertExists()
        }
    }
}

package io.github.ehb54.quiz

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import io.github.ehb54.quiz.model.QuizMode
import io.github.ehb54.quiz.model.QuizSettings
import org.junit.Test
import org.robolectric.shadows.ShadowMediaPlayer

/**
 * Typing with Peek: a tap shows the card's hint in the button's place, keeps
 * what is typed, and costs the card, which comes back next round.
 */
class PeekUiTest : QuizUiTest() {
    init {
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(60_000, 0) }
    }

    @Test
    fun aPeekShowsTheHintAndCountsAsAMiss() {
        saveDictationQuiz(QuizSettings(mode = QuizMode.Typed, shuffle = false))

        ActivityScenario.launch(MainActivity::class.java).use {
            startQuiz("Dictation", 3)
            compose.onNodeWithText("hand").assertDoesNotExist()
            compose.onNodeWithText("Answer").performTextInput("die Ha")
            compose.onNodeWithText("Peek").performClick()
            compose.waitForText("hand")
            compose.onNodeWithText("Peek").assertDoesNotExist()
            compose.onNodeWithText("die Ha").assertExists()
            compose.answer("nd")

            // The next card (the peeked one counts as still to clear) has its own Peek, its hint hidden.
            compose.waitForText("Round 1 · 3 left")
            compose.onNodeWithText("Peek").assertExists()
            compose.onNodeWithText("foot").assertDoesNotExist()
            compose.answer("der Fuß")
            compose.answer("das Auge")

            // The peeked card comes back, its hint hidden again.
            compose.waitForText("Round 2 · 1 left")
            compose.onNodeWithText("hand").assertDoesNotExist()
            compose.answer("die Hand")
            compose.waitForText("Done")
            compose.onNodeWithText("Right first time: 2 of 3").assertExists()
            compose.onNodeWithText("1 miss").assertExists()
        }
    }
}

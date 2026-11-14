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
 * Hear a word, pick its meaning: the word's spelling (its sound's text) waits
 * until the card is answered, then the whole card shows, before the next one.
 * With "Play the sound after the answer" on, its sound plays once, not again
 * when the phone is turned.
 */
class AfterAnswerUiTest : QuizUiTest() {
    init {
        // Long enough to be playing still when the phone is turned.
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(60_000, 0) }
    }

    @Test
    fun theSpellingShowsAfterThePick() {
        saveListeningQuiz(QuizSettings(mode = QuizMode.Pick, shuffle = false, choices = 3))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitForText("Listening")
            compose.onNodeWithText("Listening").performClick()
            compose.waitForText("Play the sound after the answer")
            compose.onNodeWithText("Play the sound after the answer").performClick()
            compose.onNodeWithText("Start (3 cards due)").performClick()

            for ((id, word) in listeningWords.withIndex()) {
                val (german, english) = word
                compose.waitForText("Round 1 · ${3 - id} left")
                // Only the sound is asked; its word would give the answer away.
                compose.onNodeWithText(german).assertDoesNotExist()
                if (id == 0) compose.onNodeWithContentDescription("Stop sound").performClick()
                compose.onNodeWithTag(choiceTag(id + 1L)).performClick()
                compose.waitForText("Right.")
                compose.onNodeWithText(german).assertExists()
                compose.onNodeWithText(english).assertExists()
                if (id == 0) {
                    // Its sound plays once: turning the phone shows the card again, silent.
                    compose.onNodeWithContentDescription("Stop sound").assertExists()
                    scenario.recreate()
                    compose.waitForText("Right.")
                    compose.onNodeWithText(german).assertExists()
                    compose.onNodeWithContentDescription("Play sound").assertExists()
                    compose.onNodeWithContentDescription("Stop sound").assertDoesNotExist()
                }
                compose.onNodeWithText("Next").performClick()
            }
            compose.waitForText("Done")
            compose.onNodeWithText("Right first time: 3 of 3").assertExists()
        }
    }
}

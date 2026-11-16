package io.github.ehb54.quiz

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
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
 * Looking back in a Pick session: the cards gone by, the last one first, each
 * whole, with Peek free; then the quiz goes on where it was, and nothing
 * counted.
 */
class LookBackUiTest : QuizUiTest() {
    init {
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(60_000, 0) }
    }

    private fun inLookBack(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(isDialog()))

    @Test
    fun lookingBackShowsTheCardsGoneByAndCountsNothing() {
        saveListeningQuiz(QuizSettings(mode = QuizMode.Pick, shuffle = false, choices = 3), hints = true)

        ActivityScenario.launch(MainActivity::class.java).use {
            startQuiz("Listening", 3)
            compose.onNodeWithContentDescription("Look back").assertIsNotEnabled()
            for (id in 1L..2L) {
                compose.waitForText("Round 1 · ${4 - id} left")
                compose.onNodeWithContentDescription("Stop sound").performClick()
                compose.onNodeWithTag(choiceTag(id)).performClick()
                compose.onNodeWithText("Next").performClick()
            }
            compose.waitForText("Round 1 · 1 left")

            compose.onNodeWithContentDescription("Look back").performClick()
            compose.waitForText("1 card back")
            inLookBack("der Fuß").assertExists()
            inLookBack("Later").assertIsNotEnabled()
            inLookBack("Earlier").performClick()
            compose.waitForText("2 cards back")
            inLookBack("die Hand").assertExists()
            inLookBack("Earlier").assertIsNotEnabled()
            inLookBack("Peek").performClick()
            inLookBack("the hand").assertExists()
            inLookBack("Later").performClick()
            compose.waitForText("1 card back")
            inLookBack("Back to the quiz").performClick()
            compose.waitUntil(10_000) { compose.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() }

            // Card 3 as it was; the peek while looking back cost nothing.
            compose.onNodeWithText("Round 1 · 1 left").assertExists()
            compose.onNodeWithTag(choiceTag(3)).performClick()
            compose.onNodeWithText("Next").performClick()
            compose.waitForText("Done")
            compose.onNodeWithText("Right first time: 3 of 3").assertExists()
        }
    }
}

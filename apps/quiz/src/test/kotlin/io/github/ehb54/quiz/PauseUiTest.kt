package io.github.ehb54.quiz

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import io.github.ehb54.quiz.ui.choiceTag
import org.junit.Test

/**
 * Pausing speed mode: by hand, when another app comes in front, and when the
 * app is opened again. The card is hidden while paused.
 */
class PauseUiTest : QuizUiTest() {
    @Test
    fun aPauseHidesTheCardUntilResume() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openShapesInOrder()
            compose.onNodeWithText("Speed").performClick()
            compose.onNodeWithText("Start level 1 (6 cards)").performClick()
            compose.waitForText("Level 1 · 6 left")

            compose.onNodeWithContentDescription("Pause").performClick()
            compose.waitForText("Paused")
            compose.onNodeWithTag(choiceTag(1)).assertDoesNotExist()
            compose.onNodeWithContentDescription("Pause").assertDoesNotExist()
            compose.onNodeWithText("Resume").performClick()
            compose.onNodeWithTag(choiceTag(1)).performClick()
            compose.waitForText("Level 1 · 5 left")

            // Another app in front (a call, Home): paused, and still paused on coming back.
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitForText("Paused")
            compose.onNodeWithTag(choiceTag(2)).assertDoesNotExist()
            compose.onNodeWithText("Resume").performClick()
            compose.onNodeWithTag(choiceTag(2)).performClick()
            compose.waitForText("Level 1 · 4 left")
        }
        // Closed and opened again: a fresh view model, and the run waits for Resume.
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForText("Paused")
            compose.onNodeWithText("Resume").performClick()
            compose.onNodeWithTag(choiceTag(3)).performClick()
            compose.waitForText("Level 1 · 3 left")
        }
    }
}

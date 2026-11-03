package io.github.ehb54.quiz

import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import org.junit.Test

/** Debug builds only: long-pressing the round counter leaves a session; answered cards keep their results. */
class EscapeHatchUiTest : QuizUiTest() {
    @Test
    fun longPressOnTheRoundCounterLeavesTheSession() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openShapesInOrder()
            compose.onNodeWithText("Start (6 cards due)").performClick()
            compose.answer("circle")
            compose.waitForText("Round 1 · 5 left")

            compose.onNodeWithText("Round 1 · 5 left").performTouchInput { longClick() }
            compose.waitForText("Leave this session?")
            compose.onNodeWithText("Leave").performClick()

            // Back on the quiz: circle was learned, the other five are still due.
            compose.waitForText("Start (5 cards due)")
        }
    }
}

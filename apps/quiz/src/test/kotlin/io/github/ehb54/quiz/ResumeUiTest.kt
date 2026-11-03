package io.github.ehb54.quiz

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import org.junit.Test

/** There is no leaving a session: closing the app and coming back resumes it. */
class ResumeUiTest : QuizUiTest() {
    @Test
    fun sessionSurvivesClosingTheApp() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openShapesInOrder()
            compose.onNodeWithText("Start (6 cards due)").performClick()
            compose.waitForText("Round 1 · 6 left")
            compose.answer("circle")
            compose.waitForText("Round 1 · 5 left")
        }
        // A fresh activity and view model, reading only what was saved to disk.
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForText("Round 1 · 5 left")
            compose.answer("square")
            compose.waitForText("Round 1 · 4 left")
        }
    }
}

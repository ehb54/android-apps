package io.github.ehb54.quiz

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import org.junit.Test

/** A whole Shapes session under the original program's rules, through the UI. */
class SessionRulesUiTest : QuizUiTest() {
    @Test
    fun shapesSessionFollowsTheOriginalRules() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openShapesInOrder()
            compose.onNodeWithText("Start (6 cards due)").performClick()
            compose.waitForText("Round 1 · 6 left")

            // Back does not leave a running session.
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.waitForText("Finish the quiz first: 6 cards to go.")
            compose.onNodeWithText("Round 1 · 6 left").assertExists()

            // Circle: "square" is wrong, and it is another card's answer.
            compose.answer("square")
            compose.onNodeWithText("Not quite. Try again.").assertExists()
            compose.answer("circle")
            // Square: peeking counts as a miss; the answer hides again before typing.
            compose.onNodeWithText("Show answer").performClick()
            compose.onNodeWithText("square").assertExists()
            compose.onNodeWithText("Hide it and answer").performClick()
            compose.answer("SQUARE ")
            listOf("triangle", "pentagon", "hexagon", "star").forEach { compose.answer(it) }

            // Round 2 brings back only the two missed cards.
            compose.waitForText("Round 2 · 2 left")
            compose.answer("circle")
            compose.answer("square")

            compose.waitForText("Done")
            compose.onNodeWithText("Right first time: 4 of 6").assertExists()
            compose.onNodeWithText("2 misses").assertExists()
            compose.onNodeWithText("2 rounds").assertExists()
            compose.onNodeWithText("• square").assertExists()
            compose.onNodeWithText("OK").performClick()

            // Circle recovered to level 0; square also got the confusion penalty.
            compose.waitForText("Start (2 cards due)")
            compose.onNodeWithText("level 0").assertExists()
            compose.onNodeWithText("level -2").assertExists()
        }
    }
}

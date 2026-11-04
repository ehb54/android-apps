package io.github.ehb54.quiz

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import org.junit.Test

/** A whole match session through the UI: blocks, a wrong pair, round 2, and levels kept apart. */
class MatchUiTest : ChemicalSymbolsUiTest() {
    @Test
    fun matchSessionRunsInBlocks() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            startChemicalSymbolsMatch()

            // Back does not leave a running session.
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.waitForText("Finish the quiz first: 14 cards to go.")

            // Block 1 is the first six elements; Hydrogen with He is wrong, a miss for both.
            compose.onNodeWithText("Magnesium").assertDoesNotExist()
            pair("Hydrogen", "He")
            compose.waitForText("Not a pair. Try again.")
            compose.onNodeWithText("Round 1 · 14 left").assertExists()

            matchUntilDone()
            compose.onNodeWithText("Right first time: 12 of 14").assertExists()
            compose.onNodeWithText("1 miss").assertExists()
            compose.onNodeWithText("2 rounds").assertExists()
            compose.onNodeWithText("OK").performClick()

            // Hydrogen and Helium are due again for matching; typing was not touched.
            compose.waitForText("Start (2 cards due)")
            compose.onNodeWithText("Type").performClick()
            compose.waitForText("Start (14 cards due)")
        }
    }
}

package io.github.ehb54.quiz

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import org.junit.Test

/** Whole match sessions through the UI: blocks, a wrong pair, round 2, levels kept apart, and due counts. */
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

            // Hydrogen and Helium are due again for matching; typing and picking were not touched.
            compose.waitForText("Start (2 cards due)")
            compose.onNodeWithText("Match 2").assertExists()
            compose.onNodeWithText("Type 14").assertExists()
            compose.onNodeWithText("Pick 14").assertExists()
            // The two missed in round 1, then right in round 2: level -1, then 0. The rest are at 1.
            compose.onNodeWithText("Level 0: 2 · 1: 12").assertExists()

            // Clearing those two leaves nothing to match: a check with the level every card has reached.
            compose.onNodeWithText("Start (2 cards due)").performClick()
            compose.waitForText("Round 1 · 2 left")
            matchUntilDone()
            compose.onNodeWithText("OK").performClick()
            compose.waitForText("Match ✓1")
            compose.onNodeWithText("Level 1: 14").assertExists()
            compose.onNodeWithText("Start (0 cards due)").assertIsNotEnabled()

            compose.onNodeWithContentDescription("Back").performClick()
            compose.waitForText("14 cards · Type →14 ←14 · Pick →14 ←14 · Match ✓1")
        }
    }
}

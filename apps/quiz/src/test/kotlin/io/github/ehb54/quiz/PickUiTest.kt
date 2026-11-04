package io.github.ehb54.quiz

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import org.junit.Test

/** A whole pick session through the UI: a wrong choice, round 2, the confusion penalty, levels kept apart. */
class PickUiTest : ChemicalSymbolsUiTest() {
    @Test
    fun pickSessionFollowsTheRules() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            startChemicalSymbols("Pick", "Choices")
            compose.waitForText("Hydrogen")

            // Back does not leave a running session.
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.waitForText("Finish the quiz first: 14 cards to go.")

            // A wrong choice is marked and can't be picked again; the card stays until it is right.
            val wrong = elements.map { it.second }
                .first { it != "H" && compose.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(wrong).performClick()
            compose.waitForText("Not that one. Try again.")
            compose.onNodeWithText(wrong).assertIsNotEnabled()
            compose.onNodeWithText("Hydrogen").assertExists()
            compose.onNodeWithText("H").performClick()

            // The rest right the first time, then Hydrogen again in round 2.
            for ((name, symbol) in elements.drop(1) + elements.take(1)) {
                compose.waitForText(name)
                compose.onNodeWithText(symbol).performClick()
                compose.waitForIdle()
            }
            compose.waitForText("Done")
            compose.onNodeWithText("Right first time: 13 of 14").assertExists()
            compose.onNodeWithText("1 miss").assertExists()
            compose.onNodeWithText("2 rounds").assertExists()
            // The card whose answer was picked by mistake got the confusion penalty.
            compose.onNodeWithText("• $wrong").assertExists()
            compose.onNodeWithText("OK").performClick()

            // Due again for Pick A -> B: Hydrogen and the confused card. B -> A and typing were not touched.
            compose.waitForText("Start (2 cards due)")
            compose.onNodeWithText("B → A").performClick()
            compose.waitForText("Start (14 cards due)")
            compose.onNodeWithText("Type").performClick()
            compose.onNodeWithText("A → B").performClick()
            compose.waitForText("Start (14 cards due)")
        }
    }
}

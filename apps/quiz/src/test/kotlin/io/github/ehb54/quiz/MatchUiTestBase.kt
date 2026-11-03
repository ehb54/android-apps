package io.github.ehb54.quiz

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick

/** Match-mode UI tests: the Chemical symbols sample, whose pairs the tests know. */
abstract class MatchUiTestBase : QuizUiTest() {
    protected val elements = listOf(
        "Hydrogen" to "H", "Helium" to "He", "Carbon" to "C", "Nitrogen" to "N", "Oxygen" to "O",
        "Sodium" to "Na", "Magnesium" to "Mg", "Phosphorus" to "P", "Sulfur" to "S", "Potassium" to "K",
        "Iron" to "Fe", "Copper" to "Cu", "Silver" to "Ag", "Gold" to "Au",
    )

    /** Adds Chemical symbols, opens it in match mode with shuffle off and starts: blocks follow the list. */
    protected fun startChemicalSymbolsMatch() {
        compose.waitForText("No quizzes yet", substring = true)
        openSamples()
        addSample("Chemical symbols")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Chemical symbols").performClick()
        compose.waitForText("Start (14 cards due)")
        compose.onNodeWithText("Match").performClick()
        compose.waitForText("Pairs per block")
        compose.onNodeWithText("Shuffle").performClick()
        compose.onNodeWithText("Start (14 cards due)").performClick()
        compose.waitForText("Round 1 · 14 left")
    }

    protected fun pair(left: String, right: String) {
        compose.onNodeWithText(left).performClick()
        compose.onNodeWithText(right).performClick()
        compose.waitForIdle()
    }

    /** Pairs whatever element tiles are on screen, block after block, until the Done screen. */
    protected fun matchUntilDone() {
        repeat(40) {
            if (compose.onAllNodesWithText("Done").fetchSemanticsNodes().isNotEmpty()) return
            for ((name, symbol) in elements) {
                val shown = compose.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty() &&
                    compose.onAllNodesWithText(symbol).fetchSemanticsNodes().isNotEmpty()
                if (shown) pair(name, symbol)
            }
        }
        compose.waitForText("Done")
    }
}

package io.github.ehb54.quiz

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick

/** UI tests on the Chemical symbols sample, whose pairs the tests know (pick and match modes). */
abstract class ChemicalSymbolsUiTest : QuizUiTest() {
    protected val elements = listOf(
        "Hydrogen" to "H", "Helium" to "He", "Carbon" to "C", "Nitrogen" to "N", "Oxygen" to "O",
        "Sodium" to "Na", "Magnesium" to "Mg", "Phosphorus" to "P", "Sulfur" to "S", "Potassium" to "K",
        "Iron" to "Fe", "Copper" to "Cu", "Silver" to "Ag", "Gold" to "Au",
    )

    /**
     * Adds Chemical symbols, opens it in the mode [chip] with shuffle off and
     * starts it, so cards (or blocks) follow the list. [setting] is a setting
     * only that mode shows, to wait for.
     */
    protected fun startChemicalSymbols(chip: String, setting: String) {
        compose.waitForText("No quizzes yet", substring = true)
        openSamples()
        addSample("Chemical symbols")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Chemical symbols").performClick()
        compose.waitForText("Start (14 cards due)")
        tapChip(chip)
        compose.waitForText(setting)
        compose.onNodeWithText("Shuffle").performClick()
        compose.onNodeWithText("Start (14 cards due)").performClick()
        compose.waitForText("Round 1 · 14 left")
    }

    protected fun startChemicalSymbolsMatch() = startChemicalSymbols("Match", "Pairs per block")

    /** Taps the mode or direction chip [label]; chips also show a due count ("Pick 2"). */
    protected fun tapChip(label: String) {
        compose.onNodeWithText("$label ", substring = true).performClick()
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

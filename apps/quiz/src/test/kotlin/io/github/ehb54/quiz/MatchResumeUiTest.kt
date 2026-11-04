package io.github.ehb54.quiz

import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import org.junit.Test

/** There is no leaving a match session either: closing the app and coming back resumes the block. */
class MatchResumeUiTest : ChemicalSymbolsUiTest() {
    @Test
    fun matchSessionSurvivesClosingTheApp() {
        ActivityScenario.launch(MainActivity::class.java).use {
            startChemicalSymbolsMatch()
            pair("Hydrogen", "H")
            compose.waitForText("Round 1 · 13 left")
        }
        // A fresh activity and view model, reading only what was saved to disk.
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForText("Round 1 · 13 left")
            compose.onNodeWithText("Hydrogen").assertDoesNotExist()
            pair("Helium", "He")
            compose.waitForText("Round 1 · 12 left")
        }
    }
}

package io.github.ehb54.quiz

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import org.junit.Test

/** An Excel workbook becomes one quiz per tab, named after the tab. */
class SpreadsheetImportUiTest : QuizUiTest() {
    @Test
    fun workbookTabsBecomeQuizzes() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForText("No quizzes yet", substring = true)
            compose.onNodeWithContentDescription("More options").performClick()
            compose.onNodeWithText("Add sample quizzes").performClick()
            // German starter.xlsx has the tabs Numbers and Colors.
            compose.waitForText("Added 6 sample quizzes.")
            compose.onNode(hasText("Numbers") and hasText("10 cards · 10 due")).assertExists()
            compose.onNodeWithText("Colors").performClick()
            compose.waitForText("Start (8 cards due)")
            compose.onNodeWithText("grün").assertExists()
        }
    }
}

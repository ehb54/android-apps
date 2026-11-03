package io.github.ehb54.quiz

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import org.junit.Test

/**
 * The shipped samples: an Excel workbook becomes one quiz per tab, a zip of
 * picture cards one quiz; adding samples again adds nothing twice; and a long
 * quiz starts with at most the default number of cards per session.
 */
class SpreadsheetImportUiTest : QuizUiTest() {
    @Test
    fun samplesImportOnceAndLongQuizzesStartSmall() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            addSamples()
            // Grimm nouns.xlsx: three tabs; Grimm pictures.zip: one quiz of picture cards.
            scrollListTo("Grimm nouns")
            compose.onNode(hasText("Grimm nouns") and hasText("200 cards · 200 due")).assertExists()
            scrollListTo("Grimm plurals")
            compose.onNode(hasText("Grimm plurals") and hasText("175 cards · 175 due")).assertExists()
            scrollListTo("Grimm pictures")
            compose.onNode(hasText("Grimm pictures") and hasText("81 cards · 81 due")).assertExists()

            // Adding the samples again adds nothing.
            compose.onNodeWithContentDescription("More options").performClick()
            compose.onNodeWithText("Add sample quizzes").performClick()
            compose.waitForText("All sample quizzes are already here.")
            scrollListTo("Grimm nouns")
            compose.onAllNodesWithText("Grimm nouns").assertCountEquals(1)

            // 200 due, but a session without a range asks the first 20.
            compose.onNodeWithText("Grimm nouns").performClick()
            compose.waitForText("Start (20 cards due)")
            compose.onNodeWithText("200 due in all").assertExists()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

            // German starter.xlsx: the tabs Numbers and Colors.
            compose.waitForText("Grimm nouns")
            scrollListTo("Numbers")
            compose.onNode(hasText("Numbers") and hasText("10 cards · 10 due")).assertExists()
            scrollListTo("Colors")
            compose.onNodeWithText("Colors").performClick()
            compose.waitForText("Start (8 cards due)")
            compose.onNodeWithText("grün").assertExists()
        }
    }
}

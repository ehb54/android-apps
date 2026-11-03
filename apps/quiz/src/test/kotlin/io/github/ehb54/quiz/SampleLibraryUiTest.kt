package io.github.ehb54.quiz

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import org.junit.Test

/**
 * The sample library: samples are added one by one, downloaded or built in;
 * an Excel workbook gives one quiz per tab and a zip of picture cards one quiz;
 * an added sample can be removed again; and a long quiz starts with at most
 * the default number of cards per session.
 */
class SampleLibraryUiTest : QuizUiTest() {
    @Test
    fun samplesAreAddedAndRemovedOneByOne() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitForText("No quizzes yet", substring = true)
            openSamples()
            // Grimm nouns.xlsx (online only): the tabs nouns, articles and plurals.
            scrollListTo("Grimm plurals")
            compose.onNode(hasText("Grimm plurals") and hasText("175 cards")).assertExists()
            addSample("Grimm nouns")
            scrollListTo("Grimm nouns")
            compose.onNode(hasText("Grimm nouns") and hasText("200 cards · added")).assertExists()
            compose.onNodeWithContentDescription("Add Grimm nouns").assertDoesNotExist()
            // Grimm pictures.zip: one quiz of picture cards.
            addSample("Grimm pictures")
            compose.onNode(hasText("Grimm pictures") and hasText("81 cards · 81 pictures · added")).assertExists()
            // German starter.xlsx (built in): the tabs Numbers and Colors.
            addSample("Colors")
            addSample("Numbers")

            // Removing asks first, then offers the sample again.
            pressInList("Remove Numbers")
            compose.onNodeWithText("Remove Numbers? Its cards and progress go; the sample can be added again.")
                .assertExists()
            compose.onNode(hasText("Remove") and hasAnyAncestor(isDialog())).performClick()
            waitForMessage("Removed Numbers.")
            compose.onNodeWithContentDescription("Add Numbers").assertExists()

            compose.onNodeWithContentDescription("Back").performClick()
            compose.waitForText("Grimm nouns")
            compose.onNode(hasText("Grimm nouns") and hasText("200 cards · 200 due")).assertExists()
            compose.onNode(hasText("Grimm pictures") and hasText("81 cards · 81 due")).assertExists()
            compose.onNodeWithText("Numbers").assertDoesNotExist()

            // 200 due, but a session without a range asks the first 20.
            compose.onNodeWithText("Grimm nouns").performClick()
            compose.waitForText("Start (20 cards due)")
            compose.onNodeWithText("200 due in all").assertExists()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

            compose.waitForText("Colors")
            compose.onNodeWithText("Colors").performClick()
            compose.waitForText("Start (8 cards due)")
            compose.onNodeWithText("grün").assertExists()
        }
    }
}

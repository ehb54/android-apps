package io.github.ehb54.quiz

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import io.github.ehb54.quiz.engine.parseCatalog
import org.junit.Test
import java.io.File

/**
 * The sample library: samples are added one by one, downloaded or built in;
 * an Excel workbook gives one quiz per tab and a zip of picture cards one quiz;
 * an added sample can be removed again; and a long quiz starts with at most
 * the default number of cards per session.
 */
class SampleLibraryUiTest : QuizUiTest() {
    private val catalog = parseCatalog(File("samples/catalog.json").readText())

    private fun entry(name: String) = catalog.files.flatMap { it.quizzes }.single { it.name == name }

    @Test
    fun samplesAreAddedAndRemovedOneByOne() {
        // Sizes come from the catalog, so the test holds when the word lists grow.
        val nouns = entry("History nouns").cards
        val plurals = entry("History plurals").cards
        val pictures = entry("History pictures")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitForText("No quizzes yet", substring = true)
            openSamples()
            // History nouns.xlsx (online only): the tabs nouns, articles and plurals.
            scrollListTo("History plurals")
            compose.onNode(hasText("History plurals") and hasText("$plurals cards")).assertExists()
            addSample("History nouns")
            scrollListTo("History nouns")
            compose.onNode(hasText("History nouns") and hasText("$nouns cards · added")).assertExists()
            compose.onNodeWithContentDescription("Add History nouns").assertDoesNotExist()
            // History pictures.zip: one quiz of picture cards.
            addSample("History pictures")
            compose.onNode(
                hasText("History pictures") and
                    hasText("${pictures.cards} cards · ${pictures.pictures} pictures · added"),
            ).assertExists()
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
            compose.waitForText("History nouns")
            // All due, in every mode: "200 cards · Type 200 · Pick 200 · Match 200".
            fun allDue(cards: Int) = "$cards cards · Type $cards · Pick $cards · Match $cards"
            compose.onNode(hasText("History nouns") and hasText(allDue(nouns))).assertExists()
            compose.onNode(hasText("History pictures") and hasText(allDue(pictures.cards))).assertExists()
            compose.onNodeWithText("Numbers").assertDoesNotExist()

            // All due, but a session without a range asks the first 20.
            compose.onNodeWithText("History nouns").performClick()
            compose.waitForText("Start (20 cards due)")
            compose.onNodeWithText("$nouns due in all").assertExists()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

            compose.waitForText("Colors")
            compose.onNodeWithText("Colors").performClick()
            compose.waitForText("Start (8 cards due)")
            compose.onNodeWithText("grün").assertExists()
        }
    }
}

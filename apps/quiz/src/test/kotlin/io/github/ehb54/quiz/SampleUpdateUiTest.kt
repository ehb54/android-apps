package io.github.ehb54.quiz

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import io.github.ehb54.quiz.data.SampleLibrary
import io.github.ehb54.quiz.engine.buildCatalog
import io.github.ehb54.quiz.engine.toJson
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** A sample changed on the download site shows as an update, and updating replaces the quiz. */
class SampleUpdateUiTest : QuizUiTest() {
    @get:Rule
    val site = TemporaryFolder()

    /** Puts [qzx] on the test download site as "Test words.qzx", with its catalog. */
    private fun publish(qzx: String) {
        val bytes = qzx.toByteArray()
        File(site.root, "Test words.qzx").writeBytes(bytes)
        File(site.root, "catalog.json").writeText(buildCatalog(mapOf("Test words.qzx" to bytes), emptySet()).toJson())
    }

    @Test
    fun aChangedSampleCanBeUpdated() {
        SampleLibrary.baseUrl = site.root.toURI().toString()
        publish("eins\none\nzwei\ntwo\n")
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForText("No quizzes yet", substring = true)
            openSamples()
            addSample("Test words")
            compose.onNode(hasText("Test words") and hasText("2 cards · added")).assertExists()

            publish("eins\none\nzwei\ntwo\ndrei\nthree\n")
            compose.onNodeWithContentDescription("More options").performClick()
            compose.onNodeWithText("Check for updates").performClick()
            compose.waitForText("3 cards · update available")
            pressInList("Update Test words")
            waitForMessage("Updated Test words. Progress on unchanged cards is kept.")
            compose.onNode(hasText("Test words") and hasText("3 cards · added")).assertExists()

            compose.onNodeWithContentDescription("Back").performClick()
            compose.waitForText("3 cards · Type →3 ←3 · Pick →3 ←3 · Match 3")
        }
    }
}

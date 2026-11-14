package io.github.ehb54.quiz

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ApplicationProvider
import io.github.ehb54.quiz.data.QuizStore
import io.github.ehb54.quiz.data.SampleLibrary
import io.github.ehb54.quiz.model.Quiz
import io.github.ehb54.quiz.model.QuizSettings
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Base for end-to-end tests of the app on Robolectric (the real Android
 * framework on the JVM), on a screen the size of the Galaxy S8+.
 *
 * Keep one test per class: a second test in the same JVM sees the previous
 * test's screen instead of its own, so the app's build file forks a JVM per
 * test class.
 * Native graphics mode aborts when pictures are decoded off the main thread,
 * hence the legacy mode.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@Config(qualifiers = "w412dp-h846dp-xxhdpi")
abstract class QuizUiTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    init {
        // The sample library reads the repository's samples/ folder instead of the download site.
        SampleLibrary.baseUrl = File("samples").absoluteFile.toURI().toString()
    }

    /** German words and their meanings, for [saveListeningQuiz]. */
    protected val listeningWords = listOf("die Hand" to "hand", "der Fuß" to "foot", "das Auge" to "eye")

    /**
     * Before the app starts, saves the quiz "Listening" in its store, as a zip's
     * "!hand.wav die Hand" sides give it: each card a word's sound with the word
     * for after the answer, then its meaning; card ids 1, 2, 3.
     */
    protected fun saveListeningQuiz(settings: QuizSettings) {
        val store = QuizStore(ApplicationProvider.getApplicationContext<Context>().filesDir)
        store.mediaDir.mkdirs()
        var quiz = Quiz("listening", "Listening", settings = settings)
        for ((german, english) in listeningWords) {
            File(store.mediaDir, "$english.wav").writeBytes("RIFF....WAVE".toByteArray())
            quiz = quiz.addCard(german, english, mediaA = "$english.wav")
        }
        store.saveQuiz(quiz)
    }

    /** Opens the quiz [name] from the quiz list and starts a session of its [cards] due cards. */
    protected fun startQuiz(name: String, cards: Int) {
        compose.waitForText(name)
        compose.onNodeWithText(name).performClick()
        compose.waitForText("Start ($cards cards due)")
        compose.onNodeWithText("Start ($cards cards due)").performClick()
        compose.waitForText("Round 1 · $cards left")
    }

    /** Opens the sample library from the quiz list and waits for its catalog. */
    protected fun openSamples() {
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Sample quizzes…").performClick()
        compose.waitForText("Up to date with the download site.", substring = true)
    }

    /** In the sample library: adds the sample quiz [name] and waits until it is added. */
    protected fun addSample(name: String) {
        pressInList("Add $name")
        waitForMessage("Added $name.")
    }

    /**
     * Scrolls the list to the button described as [description] and presses it.
     * The press goes through the button's click action, not a tap: lists scroll
     * under the top bar, scrolling to a node counts that area as visible, and a
     * tap there lands on the bar.
     */
    protected fun pressInList(description: String) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription(description))
        compose.onNodeWithContentDescription(description).performSemanticsAction(SemanticsActions.OnClick)
    }

    /** Taps the mode or direction chip [label]; chips also show a due count ("Pick 2"). */
    protected fun tapChip(label: String) {
        compose.onNodeWithText("$label ", substring = true).performClick()
    }

    /** Waits for the message [text], then until it goes away: it covers the bottom of the screen. */
    protected fun waitForMessage(text: String) {
        compose.waitForText(text)
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }
    }

    /** Scrolls the quiz list (or card list) until an item with [text] is on screen. */
    protected fun scrollListTo(text: String) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
    }

    /** Adds the Shapes sample, opens it and turns shuffle off: circle, square, triangle, ... */
    protected fun openShapesInOrder() {
        compose.waitForText("No quizzes yet", substring = true)
        openSamples()
        addSample("Shapes")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForText("6 cards · Type →6 · Pick →6 ←6 · Match 6")
        compose.onNodeWithText("Shapes").performClick()
        compose.waitForText("Start (6 cards due)")
        // The zip's "!circle.png" sides became pictures, not text.
        compose.onNodeWithText("!circle.png").assertDoesNotExist()
        compose.onNodeWithText("Shuffle").performClick()
    }
}

fun ComposeTestRule.waitForText(text: String, substring: Boolean = false) {
    try {
        waitUntil(10_000) { onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
    } catch (e: ComposeTimeoutException) {
        val screen = onAllNodes(isRoot(), useUnmergedTree = true).printToString(maxDepth = Int.MAX_VALUE)
        throw AssertionError("\"$text\" never appeared. On screen:\n$screen", e)
    }
}

fun ComposeTestRule.answer(text: String) {
    onNodeWithText("Answer").performTextInput(text)
    onNodeWithText("Check").performClick()
    waitForIdle()
}

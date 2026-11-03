package io.github.ehb54.quiz

import androidx.compose.ui.test.ComposeTimeoutException
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
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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

    /** Adds every sample quiz from assets/samples to an empty app. */
    protected fun addSamples() {
        compose.waitForText("No quizzes yet", substring = true)
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Add sample quizzes").performClick()
        compose.waitForText("Added $SAMPLE_QUIZZES sample quizzes.")
        // The message covers the bottom of the list until it goes away.
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Added $SAMPLE_QUIZZES sample quizzes.").fetchSemanticsNodes().isEmpty()
        }
    }

    /** Scrolls the quiz list (or card list) until an item with [text] is on screen. */
    protected fun scrollListTo(text: String) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
    }

    /** Adds the samples, opens Shapes and turns shuffle off: circle, square, triangle, ... */
    protected fun openShapesInOrder() {
        addSamples()
        scrollListTo("Shapes")
        compose.onNodeWithText("Shapes").performClick()
        compose.waitForText("Start (6 cards due)")
        // The zip's "!circle.png" sides became pictures, not text.
        compose.onNodeWithText("!circle.png").assertDoesNotExist()
        compose.onNodeWithText("Shuffle").performClick()
    }

    companion object {
        /** Quizzes in assets/samples: three .qzx, Shapes and Grimm pictures (.zip),
         *  German starter (2 tabs) and Grimm nouns (3 tabs). */
        const val SAMPLE_QUIZZES = 10
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

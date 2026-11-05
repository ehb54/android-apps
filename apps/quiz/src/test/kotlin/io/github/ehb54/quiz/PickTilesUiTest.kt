package io.github.ehb54.quiz

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import io.github.ehb54.quiz.ui.choiceTag
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

/** More than four choices: pictures sit two to a row, words keep the whole width. */
class PickTilesUiTest : QuizUiTest() {
    @Test
    fun sixPicturesTakeThreeRows() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openShapesInOrder()
            tapChip("Pick")
            compose.waitForText("Choices")
            moreChoices()
            moreChoices()

            // A -> B: a picture, and six words to pick from, one to a row.
            compose.onNodeWithText("Start (6 cards due)").performClick()
            compose.waitForText("Round 1 · 6 left")
            val words = choices()
            assertEquals(6, words.size)
            assertEquals(6, words.map { it.top }.distinct().size)
            assertEquals(1, words.map { it.left }.distinct().size)
            leaveSession()

            // B -> A: a word, and six pictures to pick from, two to a row.
            tapChip("B → A")
            compose.onNodeWithText("Start (6 cards due)").performClick()
            compose.waitForText("Round 1 · 6 left")
            val pictures = choices()
            assertEquals(6, pictures.size)
            assertEquals(3, pictures.map { it.top }.distinct().size)
            assertEquals(2, pictures.map { it.left }.distinct().size)
        }
    }

    /** Presses + on the Choices row (each setting has its own + button). */
    private fun moreChoices() {
        val row = compose.onNodeWithText("Choices").fetchSemanticsNode().boundsInRoot
        val plus = compose.onAllNodesWithText("+").fetchSemanticsNodes()
            .single { abs(it.boundsInRoot.center.y - row.center.y) < 1f }
        compose.onNode(SemanticsMatcher("Choices +") { it.id == plus.id })
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    /** Where each choice of the current card is on screen. */
    private fun choices(): List<Rect> =
        (1L..6L).flatMap { compose.onAllNodesWithTag(choiceTag(it)).fetchSemanticsNodes() }.map { it.boundsInRoot }

    /** Leaves the session through the debug build's long press on the round counter. */
    private fun leaveSession() {
        compose.onNodeWithText("Round 1 · 6 left").performTouchInput { longClick() }
        compose.waitForText("Leave this session?")
        compose.onNodeWithText("Leave").performClick()
        compose.waitForText("Start (6 cards due)")
    }
}

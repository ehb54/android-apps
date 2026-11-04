package io.github.ehb54.quiz

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import io.github.ehb54.quiz.ui.tileTag
import org.junit.Test

/** Pair sounds with letters: sound tiles play from their button and are picked by a tap elsewhere on them. */
class MorseMatchUiTest : MorseUiTest() {
    @Test
    fun pairSoundsWithLetters() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openMorse("Match", "Pairs per block")
            start()

            // Sounds on the left, letters on the right (block 1: A to F). Playing A's sound
            // doesn't pick its tile, or tapping F would be a wrong pair.
            compose.onNodeWithText("Sound 6").assertExists()
            compose.onNodeWithText("Z").assertDoesNotExist()
            compose.onNode(hasContentDescription("Play sound") and hasAnyAncestor(hasTestTag(tileTag(1, sideA = true))))
                .performClick()
            compose.onNodeWithContentDescription("Stop sound").assertExists()
            compose.onNodeWithText("F").performClick()
            compose.onNodeWithText("Not a pair. Try again.").assertDoesNotExist()
            compose.onNodeWithText("F").performClick()

            repeat(40) {
                if (compose.onAllNodesWithText("Done").fetchSemanticsNodes().isNotEmpty()) return@repeat
                for ((index, letter) in letters.withIndex()) {
                    val id = index + 1L
                    val sound = tileTag(id, sideA = true)
                    val shown = compose.onAllNodesWithTag(sound).fetchSemanticsNodes().isNotEmpty() &&
                        compose.onAllNodesWithText(letter).fetchSemanticsNodes().isNotEmpty()
                    if (shown) {
                        compose.onNodeWithTag(sound).performClick()
                        compose.onNodeWithText(letter).performClick()
                        compose.waitForIdle()
                    }
                }
            }
            compose.waitForText("Done")
            compose.onNodeWithText("Right first time: 20 of 20").assertExists()
        }
    }
}

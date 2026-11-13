package io.github.ehb54.quiz

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import io.github.ehb54.quiz.ui.tileTag
import org.junit.Test

/** Pair sounds with letters: a tap anywhere on a sound tile plays it and picks it. */
class MorseMatchUiTest : MorseUiTest() {
    @Test
    fun pairSoundsWithLetters() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openMorse("Match", "Pairs per block")
            start()

            // Sounds on the left, letters on the right (block 1: A to F). A tap on A's
            // sound plays it and picks it; tapped again it only plays again, still picked.
            compose.onNodeWithText("Sound 6").assertExists()
            compose.onNodeWithText("Z").assertDoesNotExist()
            val soundOfA = tileTag(1, sideA = true)
            compose.onNodeWithTag(soundOfA).performClick()
            compose.onNodeWithContentDescription("Stop sound").assertExists()
            compose.onNodeWithTag(soundOfA).assertIsSelected()
            compose.onNodeWithTag(soundOfA).performClick()
            compose.onNodeWithTag(soundOfA).assertIsSelected()
            compose.onNodeWithText("A").performClick()
            compose.onNodeWithText("Not a pair. Try again.").assertDoesNotExist()
            compose.onNodeWithTag(soundOfA).assertDoesNotExist()

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

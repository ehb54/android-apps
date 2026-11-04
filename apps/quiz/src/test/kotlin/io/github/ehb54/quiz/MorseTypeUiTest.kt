package io.github.ehb54.quiz

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hear a letter, type it: the sound plays by itself, and its button stops and replays it. */
class MorseTypeUiTest : MorseUiTest() {
    @Test
    fun hearTheLetterAndTypeIt() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openMorse()
            // Typing B -> A would mean typing a sound: its chip has no count.
            compose.onNodeWithText("Type 26").assertExists()
            compose.onNodeWithText("A → B 26").assertExists()
            compose.onNodeWithText("B → A").assertExists()
            start()

            compose.onNodeWithContentDescription("Stop sound").performClick()
            compose.onNodeWithContentDescription("Play sound").performClick()
            compose.onNodeWithContentDescription("Stop sound").assertExists()
            for (letter in letters.take(20)) {
                compose.onNodeWithContentDescription("Stop sound").assertExists()
                compose.answer(letter.lowercase())
            }
            compose.waitForText("Done")
            compose.onNodeWithText("Right first time: 20 of 20").assertExists()
            compose.onNodeWithText("OK").performClick()
            compose.waitForText("Start (6 cards due)")

            // The card list shows each sound with a button to play it.
            scrollListTo("Sound")
            assertTrue(compose.onAllNodesWithText("Sound").fetchSemanticsNodes().isNotEmpty())
        }
    }
}

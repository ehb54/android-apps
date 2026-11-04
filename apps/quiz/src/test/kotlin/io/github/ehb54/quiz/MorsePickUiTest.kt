package io.github.ehb54.quiz

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import io.github.ehb54.quiz.ui.choiceTag
import org.junit.Test

/** See a letter, pick its sound: each choice plays from its button and is picked by a tap elsewhere on it. */
class MorsePickUiTest : MorseUiTest() {
    @Test
    fun seeTheLetterAndPickItsSound() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openMorse("Pick", "Choices")
            tapChip("B → A")
            start()

            // Letter A with four sounds to choose from; listening picks nothing.
            compose.onNodeWithText("A").assertExists()
            compose.onNodeWithText("Sound 4").assertExists()
            compose.onAllNodesWithContentDescription("Play sound")[1].performClick()
            compose.onNodeWithContentDescription("Stop sound").assertExists()
            compose.onNodeWithText("Pick the one that belongs.").assertExists()

            // A wrong sound is marked and can't be picked again. A was missed, so it comes back in round 2.
            val wrong = (2L..26L).first { compose.onAllNodesWithTag(choiceTag(it)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag(choiceTag(wrong)).performClick()
            compose.waitForText("Not that one. Try again.")
            compose.onNodeWithTag(choiceTag(wrong)).assertIsNotEnabled()

            for (id in (1L..20L) + 1L) {
                compose.waitForText(letters[id.toInt() - 1])
                compose.onNodeWithTag(choiceTag(id)).performClick()
                compose.waitForIdle()
            }
            compose.waitForText("Done")
            compose.onNodeWithText("Right first time: 19 of 20").assertExists()
            compose.onNodeWithText("2 rounds").assertExists()
        }
    }
}

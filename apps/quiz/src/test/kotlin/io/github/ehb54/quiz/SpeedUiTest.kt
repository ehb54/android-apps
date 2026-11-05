package io.github.ehb54.quiz

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import io.github.ehb54.quiz.ui.choiceTag
import org.junit.Test

/**
 * Speed mode on Shapes: a level, its results, the next level, and stopping
 * there; the next run continues at that level. Its levels are its own, so
 * Pick's due counts are untouched.
 */
class SpeedUiTest : QuizUiTest() {
    @Test
    fun levelsFollowOneAnotherUntilStopped() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openShapesInOrder()
            compose.onNodeWithText("Speed").performClick()
            compose.waitForText("Start level 1 (6 cards)")
            compose.onNodeWithText("Tighten the time limit").assertExists()
            compose.onNodeWithText("Threshold").assertDoesNotExist()
            compose.onNodeWithText("Start level 1 (6 cards)").performClick()
            compose.waitForText("Level 1 · 6 left")

            // Miss circle once (its choice's card gets a miss too), then pick every card.
            val wrong = (2L..6L).first { compose.onAllNodesWithTag(choiceTag(it)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag(choiceTag(wrong)).performClick()
            compose.waitForText("Not that one. Try again.")
            for (id in (1L..6L) + 1L) {
                compose.onNodeWithTag(choiceTag(id)).performClick()
                compose.waitForIdle()
            }

            compose.waitForText("Finished level 1")
            compose.onNodeWithText("Right first time: 5 of 6").assertExists()
            compose.onNodeWithText("1 miss").assertExists()
            compose.onNodeWithText("Slowest cards:").assertExists()
            compose.onNodeWithText("Also marked missed, because you picked their answers for other cards:").assertExists()
            compose.onNodeWithText("Go on to level 2").performClick()
            compose.waitForText("Level 2 · 6 left")

            // One pick, then Back asks, and Stop shows the level so far.
            compose.onNodeWithTag(choiceTag(1)).performClick()
            compose.waitForText("Level 2 · 5 left")
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.waitForText("Stop speed practice?")
            compose.onNodeWithText("Cards picked so far keep their speed levels, and the next run continues at level 2.").assertExists()
            compose.onNode(hasText("Stop") and hasAnyAncestor(isDialog())).performClick()
            compose.waitForText("Stopped in level 2")
            compose.onNodeWithText("Picked 1 of 6").assertExists()
            compose.onNodeWithText("Done").performClick()

            // The run got to level 2, so the next one continues there, with every card below it.
            compose.waitForText("Continue level 2 (6 cards)")
            compose.onNodeWithContentDescription("Back").performClick()
            compose.waitForText("6 cards · Type →6 · Pick →6 ←6 · Match 6")
        }
    }
}

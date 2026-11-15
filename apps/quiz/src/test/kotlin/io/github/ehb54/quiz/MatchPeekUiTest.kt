package io.github.ehb54.quiz

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import io.github.ehb54.quiz.model.QuizMode
import io.github.ehb54.quiz.model.QuizSettings
import io.github.ehb54.quiz.ui.tileTag
import org.junit.Test
import org.robolectric.shadows.ShadowMediaPlayer

/** Matching with Peek: it shows the picked tile's hint on that tile, and its card counts as missed. */
class MatchPeekUiTest : QuizUiTest() {
    init {
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(60_000, 0) }
    }

    @Test
    fun aPeekShowsThePickedTilesHint() {
        saveDictationQuiz(QuizSettings(mode = QuizMode.Match, shuffle = false))

        ActivityScenario.launch(MainActivity::class.java).use {
            startQuiz("Dictation", 3)
            // Nothing picked, nothing to peek at.
            compose.onNodeWithText("Peek").assertIsNotEnabled()
            val sound = tileTag(1, sideA = true)
            compose.onNodeWithTag(sound).performClick()
            compose.onNodeWithText("Peek").assertIsEnabled().performClick()
            compose.onNodeWithText("hand").assertExists()
            compose.onNodeWithText("foot").assertDoesNotExist()
            // Still picked; its hint is on show, so there is nothing more to peek at.
            compose.onNodeWithTag(sound).assertIsSelected()
            compose.onNodeWithText("Peek").assertIsNotEnabled()
            compose.onNodeWithText("die Hand").performClick()
            compose.onNodeWithText("hand").assertDoesNotExist()

            // The rest, block after block: card 1 comes back next round.
            repeat(10) {
                if (compose.onAllNodesWithText("Done").fetchSemanticsNodes().isNotEmpty()) return@repeat
                for ((index, word) in listeningWords.withIndex()) {
                    val tile = tileTag(index + 1L, sideA = true)
                    val shown = compose.onAllNodesWithTag(tile).fetchSemanticsNodes().isNotEmpty() &&
                        compose.onAllNodesWithText(word.first).fetchSemanticsNodes().isNotEmpty()
                    if (shown) {
                        compose.onNodeWithTag(tile).performClick()
                        compose.onNodeWithText(word.first).performClick()
                        compose.waitForIdle()
                    }
                }
            }
            compose.waitForText("Done")
            compose.onNodeWithText("Right first time: 2 of 3").assertExists()
            compose.onNodeWithText("1 miss").assertExists()
        }
    }
}

package io.github.ehb54.quiz

import android.content.Context
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import io.github.ehb54.quiz.data.QuizStore
import io.github.ehb54.quiz.model.Quiz
import io.github.ehb54.quiz.model.QuizMode
import io.github.ehb54.quiz.model.QuizSettings
import io.github.ehb54.quiz.ui.tileTag
import org.junit.Test
import org.robolectric.shadows.ShadowMediaPlayer
import java.io.File

/**
 * Pair sounds with their meanings: a tap on a sound tile plays it and picks it,
 * and a pair made shows the word its sound says on the tile for a moment
 * before both tiles go; the hint line stays as it was.
 */
class MatchShowsPairUiTest : QuizUiTest() {
    init {
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(60_000, 0) }
    }

    @Test
    fun aPairShowsItsWordBeforeItGoes() {
        // A quiz as a zip's "!hand.wav die Hand" sides give it, already in the app's store.
        val store = QuizStore(ApplicationProvider.getApplicationContext<Context>().filesDir)
        store.mediaDir.mkdirs()
        val words = listOf("die Hand" to "hand", "der Fuß" to "foot", "das Auge" to "eye")
        var quiz = Quiz("listening", "Listening", settings = QuizSettings(mode = QuizMode.Match, shuffle = false))
        for ((german, english) in words) {
            File(store.mediaDir, "$english.wav").writeBytes("RIFF....WAVE".toByteArray())
            quiz = quiz.addCard(german, english, mediaA = "$english.wav")
        }
        store.saveQuiz(quiz)
        val hint = "Tap a tile on each side that belong together."

        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForText("Listening")
            compose.onNodeWithText("Listening").performClick()
            compose.waitForText("Start (3 cards due)")
            compose.onNodeWithText("Start (3 cards due)").performClick()
            compose.waitForText("Round 1 · 3 left")

            for ((index, word) in words.withIndex()) {
                val (german, english) = word
                val sound = tileTag(index + 1L, sideA = true)
                compose.onNodeWithTag(sound).performClick()
                compose.onNodeWithTag(sound).assertIsSelected()
                compose.onNodeWithContentDescription("Stop sound").assertExists()
                // The word would give the pair away until it is made.
                compose.onNodeWithText(german).assertDoesNotExist()
                compose.onNodeWithText(english).performClick()
                compose.onNodeWithText(german).assertExists()
                compose.onNodeWithText(hint).assertExists()
                if (index < words.lastIndex) {
                    compose.waitUntil(10_000) { compose.onAllNodesWithTag(sound).fetchSemanticsNodes().isEmpty() }
                    compose.onNodeWithText(german).assertDoesNotExist()
                    compose.onNodeWithText(english).assertDoesNotExist()
                }
            }
            compose.waitForText("Done")
            compose.onNodeWithText("Right first time: 3 of 3").assertExists()
        }
    }
}

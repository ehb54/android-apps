package io.github.ehb54.quiz

import android.content.Context
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import io.github.ehb54.quiz.data.QuizStore
import io.github.ehb54.quiz.model.Quiz
import io.github.ehb54.quiz.model.QuizMode
import io.github.ehb54.quiz.model.QuizSettings
import io.github.ehb54.quiz.ui.choiceTag
import org.junit.Test
import org.robolectric.shadows.ShadowMediaPlayer
import java.io.File

/**
 * Hear a word, pick its meaning: the word's spelling (its sound's text) waits
 * until the card is answered, then the whole card shows, before the next one.
 */
class AfterAnswerUiTest : QuizUiTest() {
    init {
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(1_000, 0) }
    }

    @Test
    fun theSpellingShowsAfterThePick() {
        // A quiz as a zip's "!hand.wav die Hand" sides give it, already in the app's store.
        val store = QuizStore(ApplicationProvider.getApplicationContext<Context>().filesDir)
        store.mediaDir.mkdirs()
        val words = listOf("die Hand" to "hand", "der Fuß" to "foot", "das Auge" to "eye")
        val settings = QuizSettings(mode = QuizMode.Pick, shuffle = false, choices = 3)
        var quiz = Quiz("listening", "Listening", settings = settings)
        for ((german, english) in words) {
            File(store.mediaDir, "$english.wav").writeBytes("RIFF....WAVE".toByteArray())
            quiz = quiz.addCard(german, english, mediaA = "$english.wav")
        }
        store.saveQuiz(quiz)

        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForText("Listening")
            compose.onNodeWithText("Listening").performClick()
            compose.waitForText("Start (3 cards due)")
            compose.onNodeWithText("Start (3 cards due)").performClick()

            for ((id, word) in words.withIndex()) {
                val (german, english) = word
                compose.waitForText("Round 1 · ${3 - id} left")
                // Only the sound is asked; its word would give the answer away.
                compose.onNodeWithText(german).assertDoesNotExist()
                compose.onNodeWithTag(choiceTag(id + 1L)).performClick()
                compose.waitForText("Right.")
                compose.onNodeWithText(german).assertExists()
                compose.onNodeWithText(english).assertExists()
                compose.onNodeWithText("Next").performClick()
            }
            compose.waitForText("Done")
            compose.onNodeWithText("Right first time: 3 of 3").assertExists()
        }
    }
}

package io.github.ehb54.quiz

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.robolectric.shadows.ShadowMediaPlayer

/**
 * UI tests on the Morse code sample: a sound for each letter on side A, the
 * letter on side B. Robolectric's media player has to be told how long a sound
 * lasts; here every sound lasts a minute, so one that started still shows its
 * stop button.
 */
abstract class MorseUiTest : QuizUiTest() {
    protected val letters = ('A'..'Z').map { it.toString() }

    init {
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(60_000, 0) }
    }

    /**
     * Adds Morse code from the sample library and opens it, in mode [chip]
     * (when given) with shuffle off, so cards come in alphabetical order.
     */
    protected fun openMorse(chip: String? = null, setting: String? = null) {
        compose.waitForText("No quizzes yet", substring = true)
        openSamples()
        addSample("Morse code")
        compose.onNode(hasText("Morse code") and hasText("26 cards · 26 sounds · added")).assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        // A sound can't be typed, so typing asks A -> B only.
        compose.waitForText("26 cards · Type →26 · Pick →26 ←26 · Match 26")
        compose.onNodeWithText("Morse code").performClick()
        compose.waitForText("Start (20 cards due)")
        if (chip != null) tapChip(chip)
        if (setting != null) compose.waitForText(setting)
        compose.onNodeWithText("Shuffle").performClick()
    }

    protected fun start() {
        compose.onNodeWithText("Start (20 cards due)").performClick()
        compose.waitForText("Round 1 · 20 left")
    }
}

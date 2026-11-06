package io.github.ehb54.quiz.model

import io.github.ehb54.quiz.model.QuizSettings.Companion.sessionSizeSteps
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionSizeStepsTest {
    private val small = listOf(5, 10, 15, 20, 25, 30, 40, 50, 75, 100)

    @Test
    fun stepsStopBelowTheQuizSizeThenAll() {
        assertEquals(listOf(0), sessionSizeSteps(0))
        assertEquals(listOf(5, 10, 0), sessionSizeSteps(12))
        // A step equal to the quiz's size would be the same as all.
        assertEquals(listOf(5, 10, 15, 20, 25, 30, 40, 50, 75, 0), sessionSizeSteps(100))
        assertEquals(small + listOf(250, 0), sessionSizeSteps(300))
    }

    @Test
    fun longListsKeepDoubling() {
        assertEquals(small + listOf(250, 500, 1000, 2000, 0), sessionSizeSteps(3000))
        assertEquals(small + listOf(250, 500, 1000, 2000, 4000, 8000, 0), sessionSizeSteps(10_000))
    }

    @Test
    fun theLargestSizeStillEnds() {
        val steps = sessionSizeSteps(Int.MAX_VALUE)
        assertEquals(0, steps.last())
        assertEquals(250 shl 23, steps[steps.size - 2]) // 2,097,152,000; doubling again would overflow
    }
}

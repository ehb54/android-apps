package io.github.ehb54.quiz.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StopwatchTest {
    private var now = 1000L
    private val clock = { now }

    @Test
    fun aPauseStopsTheClockAndTimeBeforeItStillCounts() {
        val watch = Stopwatch(clock)
        now += 300
        watch.running = false
        now += 5000
        assertEquals(300L, watch.millis)
        watch.running = false
        watch.running = true
        watch.running = true
        now += 200
        assertEquals(500L, watch.millis)
    }

    @Test
    fun aRestartCountsFromZeroAndWaitsWhilePaused() {
        val watch = Stopwatch(clock)
        now += 400
        watch.restart()
        now += 100
        assertEquals(100L, watch.millis)

        watch.running = false
        watch.restart()
        now += 900
        assertEquals(0L, watch.millis)
        assertFalse(watch.running)
        watch.running = true
        now += 50
        assertEquals(50L, watch.millis)
    }

    @Test
    fun aStopwatchMadeWhilePausedWaitsForResume() {
        val watch = Stopwatch(clock, running = false)
        now += 700
        assertEquals(0L, watch.millis)
        watch.running = true
        assertTrue(watch.running)
        now += 250
        assertEquals(250L, watch.millis)
    }
}

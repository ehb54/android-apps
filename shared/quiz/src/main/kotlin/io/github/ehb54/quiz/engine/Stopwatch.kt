package io.github.ehb54.quiz.engine

/**
 * How long a card has been on screen for its answer, counting only while
 * [running]: a paused session (or one whose app is away) stops it, and time
 * before the pause still counts. [now] is a clock in milliseconds that never
 * goes back (on Android, SystemClock.elapsedRealtime).
 */
class Stopwatch(private val now: () -> Long, running: Boolean = true) {
    private var counted = 0L
    private var since: Long? = if (running) now() else null

    /** The time counted so far. */
    val millis: Long get() = counted + (since?.let { now() - it } ?: 0)

    var running: Boolean
        get() = since != null
        set(value) {
            val start = since
            if (value && start == null) {
                since = now()
            } else if (!value && start != null) {
                counted += now() - start
                since = null
            }
        }

    /** Starts counting again from 0, e.g. when a card's sound has played; stays stopped while paused. */
    fun restart() {
        counted = 0
        if (since != null) since = now()
    }
}

package io.github.ehb54.quiz.engine

import io.github.ehb54.quiz.model.Direction
import io.github.ehb54.quiz.model.Quiz
import io.github.ehb54.quiz.model.QuizMode
import io.github.ehb54.quiz.model.QuizSettings
import io.github.ehb54.quiz.model.SpeedPlace
import io.github.ehb54.quiz.model.Stats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SpeedTest {
    private val random = Random(5)

    private fun quiz(settings: QuizSettings = QuizSettings()) =
        listOf("Hydrogen" to "H", "Helium" to "He", "Carbon" to "C", "Nitrogen" to "N", "Oxygen" to "O")
            .fold(Quiz("q", "test", settings = settings.copy(mode = QuizMode.Speed, shuffle = false))) { q, (a, b) ->
                q.addCard(a, b)
            }

    private fun start(quiz: Quiz) = Step(quiz, startSpeedRun(quiz, random)!!, Outcome.Right)

    private fun Step.pick(id: Long, millis: Long) = pickAnswer(quiz, session, id, millis, random)

    /** Picks every card of the level right, the n-th card (from 1) taking [millis] (n). */
    private fun Step.playLevel(millis: (Int) -> Long = { 1000L }): Step {
        var step = this
        var n = 0
        val level = step.session.speed!!.level
        while (step.session.speed!!.level == level) step = step.pick(step.session.current!!, millis(++n))
        return step
    }

    @Test
    fun aLevelEndsInTheNextOneWithItsTimes() {
        var step = start(quiz())
        assertEquals(1, step.session.speed!!.level)
        assertEquals(5, step.session.total)
        assertEquals(4, step.session.choices.size)

        step = step.playLevel { it * 100L }
        val session = step.session
        assertFalse(session.finished)
        assertEquals(2, session.speed!!.level)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), session.queue)
        val result = session.speed.shown!!
        assertEquals(listOf(300L, 300L), listOf(session.speed.previousAverageMillis, session.speed.previousMedianMillis))
        assertEquals(1, result.level)
        assertEquals(listOf(5, 5, 5, 0), listOf(result.cards, result.answered, result.firstTry, result.misses))
        assertEquals(listOf(300L, 300L, 100L, 500L), listOf(result.averageMillis, result.medianMillis, result.fastestMillis, result.slowestMillis))
        assertEquals(listOf(CardTime(5, 500), CardTime(4, 400), CardTime(3, 300)), result.slowest)
        assertEquals(0L, result.previousAverageMillis)

        // Its own record: Pick's is untouched, and each card keeps its latest time.
        step.quiz.cards.forEach {
            assertEquals(Stats(asked = 1, rightFirst = 1), it.statsSpeedAToB)
            assertEquals(Stats(), it.statsPickAToB)
            assertEquals(it.id * 100, it.speedMillisAToB)
        }

        // The next level compares with this one.
        step = Step(step.quiz, goOn(step.session), Outcome.Right).playLevel { 200L }
        val second = step.session.speed!!.shown!!
        assertEquals(2, second.level)
        assertEquals(listOf(200L, 300L), listOf(second.averageMillis, second.previousAverageMillis))
        assertEquals(300L, second.previousMedianMillis)
    }

    @Test
    fun aMissedCardComesBackInTheLevelAndIsNotTimed() {
        var step = start(quiz())
        val wrong = step.session.choices.first { it != 1L }
        step = step.pick(wrong, 100)
        assertEquals(Outcome.Wrong, step.outcome)
        step = step.playLevel { 1000L }
        val result = step.session.speed!!.shown!!
        // Card 1 was missed, asked again in round 2, and is not among the times.
        assertEquals(listOf(4, 1), listOf(result.firstTry, result.misses))
        assertEquals(listOf(2L, 3L, 4L), result.slowest.map { it.card })
        assertEquals(listOf(wrong), result.confused)
        assertEquals(0L, step.quiz.card(1)!!.speedMillisAToB)
        // Card 1: a miss, then right in round 2. The card picked for it got a miss too.
        assertEquals(Stats(asked = 2, rightFirst = 1, wrong = 1), step.quiz.card(1)!!.statsSpeedAToB)
        assertEquals(Stats(asked = 1, rightFirst = 0, wrong = 1), step.quiz.card(wrong)!!.statsSpeedAToB)
    }

    @Test
    fun aRunStartsAboveTheLowestLevelAndAsksOnlyTheCardsBelowIt() {
        var quiz = quiz(QuizSettings(direction = Direction.BToA))
        quiz.cards.forEach { quiz = quiz.replaceCard(it.withSpeedStats(Direction.BToA, Stats(asked = 3, rightFirst = 3))) }
        quiz = quiz.replaceCard(quiz.card(2)!!.withSpeedStats(Direction.BToA, Stats(asked = 1, rightFirst = 1)))
        assertEquals(2, quiz.speedStartLevel())
        assertFalse(quiz.speedContinues())
        val session = startSpeedRun(quiz, random)!!
        assertEquals(Direction.BToA, session.direction)
        assertEquals(listOf(2L), session.queue)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), session.speed!!.cards)

        // A place below that level is passed by; the other direction's place is not this one's.
        quiz = quiz.withSpeedPlace(Direction.BToA, SpeedPlace(1, 500, 500)).withSpeedPlace(Direction.AToB, SpeedPlace(9))
        assertEquals(2, quiz.speedStartLevel())
        assertFalse(quiz.speedContinues())
        assertEquals(0L, startSpeedRun(quiz, random)!!.speed!!.previousAverageMillis)
    }

    @Test
    fun aRunStoppedInALevelContinuesThere() {
        // Cards 2 and 3 are at level 1, the rest at 3, and the place (level 1) is behind: the run starts at level 2.
        var quiz = quiz().withSpeedPlace(Direction.AToB, SpeedPlace(1, 500, 500))
        quiz.cards.forEach {
            val rightFirst = if (it.id in 2L..3L) 1 else 3
            quiz = quiz.replaceCard(it.withSpeedStats(Direction.AToB, Stats(asked = 3, rightFirst = rightFirst)))
        }
        var step = start(quiz)
        assertEquals(listOf(2L, 3L), step.session.queue)
        assertEquals(0L, step.session.speed!!.previousAverageMillis)

        // A miss on card 2, then it is picked: it drops to level -1, and the quiz now keeps level 2.
        step = step.pick(step.session.choices.first { it != 2L }, 100).pick(2, 100)
        assertEquals(-1, step.quiz.card(2)!!.speedStats(Direction.AToB).level)
        assertEquals(SpeedPlace(2), step.quiz.speedPlace(Direction.AToB))
        assertEquals(2, step.quiz.speedStartLevel())
    }

    @Test
    fun aRunContinuesAtTheLevelItGotTo() {
        // Level 1 with a miss on card 1, then level 2: the quiz keeps level 3 as its place.
        var step = start(quiz(QuizSettings(tighten = true)))
        assertNull(step.quiz.speedPlace(Direction.AToB))
        val wrong = step.session.choices.first { it != 1L }
        step = step.pick(wrong, 100).pick(1, 1000)
        assertEquals(SpeedPlace(1), step.quiz.speedPlace(Direction.AToB))
        step = step.playLevel { 1000L }
        assertEquals(SpeedPlace(2, 1000, 1000), step.quiz.speedPlace(Direction.AToB))
        step = Step(step.quiz, goOn(step.session), Outcome.Right).playLevel { 800L }
        var quiz = step.quiz
        assertEquals(SpeedPlace(3, 800, 800), quiz.speedPlace(Direction.AToB))
        assertNull(quiz.speedPlace(Direction.BToA))

        // Without the place, the missed cards (now at levels 1 and 0) would start a run at level 1.
        assertEquals(listOf(1, 0), listOf(1L, wrong).map { quiz.card(it)!!.speedStats(Direction.AToB).level })
        assertEquals(3, quiz.speedStartLevel())
        assertTrue(quiz.speedContinues())
        val session = startSpeedRun(quiz, random)!!
        val speed = session.speed!!
        assertEquals(3, speed.level)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), session.queue)
        assertEquals(listOf(800L, 800L, 1200L), listOf(speed.previousAverageMillis, speed.previousMedianMillis, speed.limitMillis))

        // Stopping in the middle of level 3 keeps the place; a run after that plays level 3 again.
        step = Step(quiz, session, Outcome.Right).pick(1, 700)
        quiz = step.quiz
        assertEquals(SpeedPlace(3, 800, 800), quiz.speedPlace(Direction.AToB))
        assertEquals(3, startSpeedRun(quiz, random)!!.speed!!.level)

        // Reset statistics forgets the place with the levels.
        assertNull(quiz.resetStats().speedPlace(Direction.AToB))
        assertEquals(1, quiz.resetStats().speedStartLevel())
    }

    @Test
    fun theRunsCardsAreTheRangeOrTheFirstCardsPerSession() {
        assertEquals(listOf(1L, 2L, 3L), quiz(QuizSettings(sessionSize = 3)).speedCards().map { it.id })
        assertEquals(listOf(2L, 3L, 4L, 5L), quiz(QuizSettings(sessionSize = 3, rangeFrom = 2)).speedCards().map { it.id })
        assertNull(startSpeedRun(Quiz("q", "empty", settings = QuizSettings(mode = QuizMode.Speed)), random))
    }

    @Test
    fun theTimerLimitsEveryLevelAndTighteningFollowsTheMedian() {
        // Timer only: 2 s each level, and a slower pick is a miss.
        var step = start(quiz(QuizSettings(timerSeconds = 2)))
        assertEquals(2000L, step.session.limitMillis)
        assertEquals(Outcome.TooSlow, step.pick(1, 2500).outcome)
        step = step.playLevel { 1000L }
        assertEquals(2000L, step.session.limitMillis)

        // Tightening: 1.5 x the median, rounded up to a tenth, never above the timer.
        step = start(quiz(QuizSettings(tighten = true)))
        assertEquals(0L, step.session.limitMillis)
        step = step.playLevel { 1000L + it }
        assertEquals(1600L, step.session.limitMillis)
        step = start(quiz(QuizSettings(tighten = true, timerSeconds = 1))).playLevel { 900L }
        assertEquals(1000L, step.session.limitMillis)
    }

    @Test
    fun stoppingShowsTheLevelSoFar() {
        var step = start(quiz())
        step = step.pick(1, 400).pick(2, 600)
        val stopped = stopLevel(step.session).speed!!.shown!!
        assertEquals(listOf(5, 2), listOf(stopped.cards, stopped.answered))
        assertEquals(500L, stopped.medianMillis)
        assertTrue(stopped.stopped)
    }

    @Test
    fun theMedianOfAnEvenCountIsTheMeanOfTheMiddleTwo() {
        assertEquals(0L, median(emptyList()))
        assertEquals(5L, median(listOf(5)))
        assertEquals(15L, median(listOf(10, 20)))
        assertEquals(20L, median(listOf(10, 20, 90)))
    }
}

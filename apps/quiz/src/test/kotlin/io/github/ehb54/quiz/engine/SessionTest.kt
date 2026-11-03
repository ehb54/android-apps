package io.github.ehb54.quiz.engine

import io.github.ehb54.quiz.model.Direction
import io.github.ehb54.quiz.model.Quiz
import io.github.ehb54.quiz.model.QuizSettings
import io.github.ehb54.quiz.model.Stats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTest {
    private fun quiz(vararg pairs: Pair<String, String>, settings: QuizSettings = QuizSettings(shuffle = false)) =
        pairs.fold(Quiz("q", "test", settings = settings)) { q, (a, b) -> q.addCard(a, b) }

    private fun Step.answer(typed: String, millis: Long = 1000) = submitAnswer(quiz, session, typed, millis)

    private fun start(quiz: Quiz) = Step(quiz, startSession(quiz)!!, Outcome.Right)

    @Test
    fun rightFirstTimeFinishesAndRaisesLevel() {
        val end = start(quiz("cat" to "chat", "dog" to "chien"))
            .answer("chat")
            .answer(" CHIEN ")
        assertTrue(end.session.finished)
        assertEquals(2, end.session.firstTry)
        end.quiz.cards.forEach { assertEquals(Stats(asked = 1, rightFirst = 1), it.statsAToB) }
    }

    @Test
    fun missedCardComesBackNextRoundAndResetsStreak() {
        var quiz = quiz("cat" to "chat", "dog" to "chien")
        quiz = quiz.replaceCard(quiz.cards[0].copy(statsAToB = Stats(asked = 3, rightFirst = 0)))
        var step = start(quiz).answer("chien")
        assertEquals(Outcome.Wrong, step.outcome)
        step = step.answer("chat")
        assertEquals(Outcome.Right, step.outcome)
        assertEquals(Stats(asked = 4, rightFirst = 0, wrong = 1), step.quiz.cards[0].statsAToB)

        step = step.answer("chien")
        assertEquals(2, step.session.round)
        assertEquals(listOf(1L), step.session.queue)

        step = step.answer("chat")
        assertTrue(step.session.finished)
        assertEquals(Stats(asked = 5, rightFirst = 1, wrong = 1), step.quiz.cards[0].statsAToB)
        assertEquals(0, step.quiz.cards[0].statsAToB.level)
    }

    @Test
    fun confusionPenalizesTheCardWhoseAnswerWasTyped() {
        val end = start(quiz("cat" to "chat", "dog" to "chien"))
            .answer("Chien")
            .answer("chat")
            .answer("chien")
            .answer("chat")
        assertTrue(end.session.finished)
        assertEquals(listOf(2L), end.confused.map { it.id })
        assertEquals(Stats(asked = 1, rightFirst = 0, wrong = 1), end.quiz.card(2)!!.statsAToB)
    }

    @Test
    fun revealCountsAsAMiss() {
        val start = start(quiz("cat" to "chat"))
        val step = submitAnswer(start.quiz, revealAnswer(start.session), "chat", 1000)
        assertFalse(step.session.finished)
        assertEquals(1, step.session.misses)
        assertEquals(1, step.quiz.cards[0].statsAToB.wrong)
    }

    @Test
    fun slowAnswerIsAMissEvenWhenRight() {
        val start = start(quiz("cat" to "chat", settings = QuizSettings(timerSeconds = 5)))
        val step = start.answer("chat", millis = 5001)
        assertEquals(Outcome.TooSlow, step.outcome)
        assertEquals(1, step.session.missesOnCurrent)
        assertEquals(1, step.answer("chat", millis = 4000).quiz.cards[0].statsAToB.wrong)
    }

    @Test
    fun missesAreCappedAtThree() {
        var stats = Stats()
        repeat(5) { stats = stats.answeredAfterMiss() }
        assertEquals(Stats.MAX_WRONG, stats.wrong)
        assertEquals(5, stats.asked)
    }

    @Test
    fun cardsAtThresholdAreSkippedAndThresholdZeroAsksAll() {
        var quiz = quiz("cat" to "chat", "dog" to "chien")
        quiz = quiz.replaceCard(quiz.cards[0].copy(statsAToB = Stats(rightFirst = 2)))
        assertEquals(listOf(2L), startSession(quiz)!!.queue)

        // Old misses raise the bar: two straight rights after two misses is still level 0.
        val missedBefore = quiz.replaceCard(quiz.cards[1].copy(statsAToB = Stats(rightFirst = 2, wrong = 2)))
        assertEquals(listOf(2L), startSession(missedBefore)!!.queue)

        val allLearned = quiz.replaceCard(quiz.cards[1].copy(statsAToB = Stats(rightFirst = 1)))
        assertNull(startSession(allLearned))
        val askAll = allLearned.copy(settings = allLearned.settings.copy(threshold = 0))
        assertEquals(2, startSession(askAll)!!.total)
    }

    @Test
    fun withoutARangeASessionTakesTheFirstDueCardsUpToTheSessionSize() {
        val pairs = (1..30).map { "$it" to "answer $it" }.toTypedArray()
        val quiz = quiz(*pairs)
        assertEquals((1L..20L).toList(), startSession(quiz)!!.queue)

        val learned = quiz.replaceCard(quiz.cards[0].copy(statsAToB = Stats(rightFirst = 1)))
        assertEquals((2L..21L).toList(), startSession(learned)!!.queue)

        assertEquals(30, startSession(quiz.copy(settings = quiz.settings.copy(sessionSize = 0)))!!.total)
        val ranged = quiz.copy(settings = quiz.settings.copy(rangeFrom = 1, rangeTo = 25))
        assertEquals(25, startSession(ranged)!!.total)
    }

    @Test
    fun replacingAQuizKeepsProgressOnUnchangedCards() {
        var old = quiz("cat" to "chat", "dog" to "chien").copy(name = "My French")
        old = old.replaceCard(old.cards[0].copy(statsAToB = Stats(asked = 3, rightFirst = 2)))
        val fresh = quiz("dog" to "chien", "cat" to "chat", "bird" to "oiseau").copy(sourceVersion = "v2")

        val replaced = old.replacedBy(fresh)
        assertEquals("My French", replaced.name)
        assertEquals(listOf("dog", "cat", "bird"), replaced.cards.map { it.a })
        assertEquals(listOf(1L, 2L, 3L), replaced.cards.map { it.id })
        assertEquals(Stats(asked = 3, rightFirst = 2), replaced.cards[1].statsAToB)
        assertEquals(Stats(), replaced.cards[2].statsAToB)
        assertEquals(4L, replaced.nextCardId)
        assertEquals("v2", replaced.sourceVersion)
    }

    @Test
    fun rangeLimitsTheSession() {
        val quiz = quiz("1" to "one", "2" to "two", "3" to "three", "4" to "four")
            .let { it.copy(settings = it.settings.copy(rangeFrom = 2, rangeTo = 3)) }
        assertEquals(listOf(2L, 3L), startSession(quiz)!!.queue)
    }

    @Test
    fun pictureSidesAreOnlyPrompts() {
        val quiz = Quiz("q", "test", settings = QuizSettings(shuffle = false))
            .addCard("", "caffeine", imageA = "caffeine.png")
        assertEquals(listOf(1L), startSession(quiz)!!.queue)
        assertNull(startSession(quiz.copy(settings = quiz.settings.copy(direction = Direction.BToA))))
    }
}

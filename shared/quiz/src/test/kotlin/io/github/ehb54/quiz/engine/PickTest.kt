package io.github.ehb54.quiz.engine

import io.github.ehb54.quiz.model.Direction
import io.github.ehb54.quiz.model.Quiz
import io.github.ehb54.quiz.model.QuizMode
import io.github.ehb54.quiz.model.QuizSettings
import io.github.ehb54.quiz.model.Stats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PickTest {
    private val random = Random(3)

    private fun quiz(vararg pairs: Pair<String, String>, choices: Int = 4, direction: Direction = Direction.AToB) =
        pairs.fold(
            Quiz("q", "test", settings = QuizSettings(mode = QuizMode.Pick, shuffle = false, choices = choices, direction = direction)),
        ) { q, (a, b) -> q.addCard(a, b) }

    private val elements = quiz("Hydrogen" to "H", "Helium" to "He", "Carbon" to "C", "Nitrogen" to "N", "Oxygen" to "O")

    private fun start(quiz: Quiz) = Step(quiz, startPickSession(quiz, random)!!, Outcome.Right)

    private fun Step.pick(id: Long, millis: Long = 1000) = pickAnswer(quiz, session, id, millis, random)

    /** A choice on screen that is not the current card's. */
    private fun Step.wrongChoice() = session.choices.first { it != session.current }

    @Test
    fun eachCardOffersItsAnswerAmongDistinctChoices() {
        val step = start(elements)
        assertEquals(4, step.session.choices.size)
        assertTrue(1L in step.session.choices)
        assertEquals(4, step.session.choices.map { step.quiz.card(it)!!.b }.toSet().size)
    }

    @Test
    fun rightFirstTimeRaisesThePickLevelOnly() {
        var step = start(elements)
        while (!step.session.finished) step = step.pick(step.session.current!!)
        assertEquals(5, step.session.firstTry)
        step.quiz.cards.forEach {
            assertEquals(Stats(asked = 1, rightFirst = 1), it.statsPickAToB)
            assertEquals(Stats(), it.statsPickBToA)
            assertEquals(Stats(), it.statsAToB)
            assertEquals(Stats(), it.statsMatch)
        }
    }

    @Test
    fun aWrongPickIsRuledOutTheCardStaysAndComesBack() {
        var step = start(elements)
        val wrong = step.wrongChoice()
        step = step.pick(wrong)
        assertEquals(Outcome.Wrong, step.outcome)
        assertEquals(1L, step.session.current)
        assertEquals(setOf(wrong), step.session.ruledOut)
        assertEquals(Outcome.Ignored, step.pick(wrong).outcome)

        step = step.pick(1)
        assertEquals(Stats(asked = 1, rightFirst = 0, wrong = 1), step.quiz.card(1)!!.statsPickAToB)
        assertTrue(step.session.ruledOut.isEmpty())
        while (step.session.round == 1) step = step.pick(step.session.current!!)
        assertEquals(listOf(1L), step.session.queue)
        assertTrue(1L in step.session.choices)

        step = step.pick(1)
        assertTrue(step.session.finished)
        assertEquals(0, step.quiz.card(1)!!.statsPickAToB.level)
        // The card whose answer was picked wrongly got the confusion penalty.
        assertEquals(listOf(wrong), step.confused.map { it.id })
        assertEquals(-1, step.quiz.card(wrong)!!.statsPickAToB.level)
    }

    @Test
    fun aSlowPickIsAMissEvenWhenRight() {
        val quiz = elements.copy(settings = elements.settings.copy(timerSeconds = 5))
        val step = start(quiz).pick(1, millis = 5001)
        assertEquals(Outcome.TooSlow, step.outcome)
        assertEquals(1L, step.session.current)
        assertTrue(step.session.ruledOut.isEmpty())
    }

    @Test
    fun pickLevelsDecideWhatIsDuePerDirection() {
        var quiz = elements
        quiz = quiz.replaceCard(quiz.cards[0].copy(statsPickAToB = Stats(rightFirst = 1)))
        quiz = quiz.replaceCard(quiz.cards[1].copy(statsAToB = Stats(rightFirst = 5), statsMatch = Stats(rightFirst = 5)))
        assertEquals(listOf(2L, 3L, 4L, 5L), quiz.sessionCards().map { it.id })
        val backwards = quiz.copy(settings = quiz.settings.copy(direction = Direction.BToA))
        assertEquals(5, backwards.sessionCards().size)
        val session = startPickSession(backwards, random)!!
        assertTrue(session.choices.all { backwards.card(it)!!.a.isNotBlank() })
    }

    @Test
    fun picturesCanBeChoicesButTypedSessionsSkipThem() {
        val quiz = Quiz("q", "test", settings = QuizSettings(mode = QuizMode.Pick, direction = Direction.BToA))
            .addCard("", "caffeine", imageA = "caffeine.png")
            .addCard("", "water", imageA = "water.png")
        assertEquals(2, startPickSession(quiz, random)!!.total)
        assertNull(startSession(quiz.copy(settings = quiz.settings.copy(mode = QuizMode.Typed)), random))
    }

    @Test
    fun cardsShowingTheSameAnswerAreNeverChoicesTogether() {
        val quiz = quiz("das Mal" to "time", "die Zeit" to "Time", "das Jahr" to "year")
        val choices = startPickSession(quiz, random)!!.choices
        assertEquals(setOf(1L, 3L), choices.toSet())
    }

    @Test
    fun aSmallQuizOffersFewerChoices() {
        assertEquals(2, startPickSession(quiz("a" to "1", "b" to "2"), random)!!.choices.size)
    }
}

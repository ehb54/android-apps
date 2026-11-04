package io.github.ehb54.quiz.engine

import io.github.ehb54.quiz.model.Quiz
import io.github.ehb54.quiz.model.QuizMode
import io.github.ehb54.quiz.model.QuizSettings
import io.github.ehb54.quiz.model.Stats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MatchTest {
    private val random = Random(7)

    private fun quiz(count: Int, blockSize: Int = 6, sessionSize: Int = 0) =
        (1..count).fold(
            Quiz("q", "test", settings = QuizSettings(mode = QuizMode.Match, shuffle = false, blockSize = blockSize, sessionSize = sessionSize)),
        ) { q, n -> q.addCard("a$n", "b$n") }

    /** Pairs every scored card of the block on screen; fillers too, so the block clears. */
    private fun MatchStep.clearBlock(): MatchStep {
        var step = this
        for (id in session.left) step = pairTiles(step.quiz, step.session, id, id, random)
        return step
    }

    private fun start(quiz: Quiz) = MatchStep(quiz, startMatch(quiz, random)!!, PairOutcome.Paired)

    @Test
    fun cardsComeInBlocks() {
        var step = start(quiz(24))
        val blocks = mutableListOf<List<Long>>()
        while (!step.session.finished) {
            blocks += step.session.scored
            assertEquals(step.session.scored.toSet(), step.session.right.toSet())
            step = step.clearBlock()
        }
        assertEquals(listOf((1L..6L).toList(), (7L..12L).toList(), (13L..18L).toList(), (19L..24L).toList()), blocks)
        assertEquals(24, step.session.firstTry)
        assertEquals(1, step.session.round)
        step.quiz.cards.forEach {
            assertEquals(Stats(asked = 1, rightFirst = 1), it.statsMatch)
            assertEquals(Stats(), it.statsAToB)
        }
    }

    @Test
    fun aWrongPairIsAMissForBothCardsAndTheyComeBack() {
        var step = start(quiz(6))
        step = pairTiles(step.quiz, step.session, 1, 2, random)
        assertEquals(PairOutcome.Wrong, step.outcome)
        assertEquals(setOf(1L, 2L), step.session.missed)
        step = step.clearBlock()
        assertEquals(2, step.session.round)
        assertEquals(listOf(1L, 2L), step.session.scored)
        assertEquals(Stats(asked = 1, rightFirst = 0, wrong = 1), step.quiz.card(1)!!.statsMatch)
        assertEquals(Stats(asked = 1, rightFirst = 1), step.quiz.card(3)!!.statsMatch)

        step = step.clearBlock()
        assertTrue(step.session.finished)
        assertEquals(PairOutcome.Finished, step.outcome)
        assertEquals(4, step.session.firstTry)
        assertEquals(1, step.session.misses)
        assertEquals(0, step.quiz.card(1)!!.statsMatch.level)
    }

    @Test
    fun aShortBlockIsToppedUpWithUnscoredFillers() {
        var step = start(quiz(8))
        step = step.clearBlock()
        val block = step.session
        assertEquals(listOf(7L, 8L), block.scored)
        assertEquals(6, block.left.size)
        val fillers = block.left - block.scored.toSet()
        assertTrue(fillers.all { it in 1L..6L })

        step = step.clearBlock()
        assertTrue(step.session.finished)
        // Fillers were cleared in block 1 and not scored again.
        fillers.forEach { assertEquals(1, step.quiz.card(it)!!.statsMatch.asked) }
    }

    @Test
    fun aWrongPairWithAFillerCountsOnlyForTheScoredCard() {
        var step = start(quiz(8)).clearBlock()
        val filler = (step.session.left - step.session.scored.toSet()).first()
        step = pairTiles(step.quiz, step.session, 7, filler, random)
        assertEquals(setOf(7L), step.session.missed)
    }

    @Test
    fun matchLevelsDecideWhatIsDueAndTypedLevelsDoNot() {
        var quiz = quiz(3)
        quiz = quiz.replaceCard(quiz.cards[0].copy(statsMatch = Stats(rightFirst = 1)))
        quiz = quiz.replaceCard(quiz.cards[1].copy(statsAToB = Stats(rightFirst = 5)))
        assertEquals(listOf(2L, 3L), startMatch(quiz, random)!!.scored)
        // Typed A -> B ignores the match level: card 1 is due there, card 2 is not.
        val typed = quiz.copy(settings = quiz.settings.copy(mode = QuizMode.Typed))
        assertEquals(listOf(1L, 3L), typed.due().map { it.id })
    }

    @Test
    fun cardsShowingTheSameThingCanBePairedEitherWay() {
        val quiz = Quiz("q", "test", settings = QuizSettings(mode = QuizMode.Match, shuffle = false))
            .addCard("das Mal", "time")
            .addCard("die Zeit", "Time ")
        var step = start(quiz)
        step = pairTiles(step.quiz, step.session, 1, 2, random)
        assertEquals(PairOutcome.Paired, step.outcome)
        step = pairTiles(step.quiz, step.session, 2, 1, random)
        assertEquals(PairOutcome.Finished, step.outcome)
        assertEquals(2, step.session.firstTry)
    }

    @Test
    fun picturesAreTilesToo() {
        val quiz = Quiz("q", "test", settings = QuizSettings(mode = QuizMode.Match))
            .addCard("", "caffeine", mediaA = "caffeine.png")
            .addCard("", "", mediaA = "a.png", mediaB = "b.png")
        assertEquals(2, startMatch(quiz, random)!!.total)
    }

    @Test
    fun theSessionCapAppliesWithoutARange() {
        assertEquals(20, startMatch(quiz(30, sessionSize = 20), random)!!.total)
        assertNull(startMatch(quiz(0), random))
    }

    @Test
    fun pairingAClearedTileIsIgnored() {
        var step = start(quiz(6))
        step = pairTiles(step.quiz, step.session, 1, 1, random)
        val again = pairTiles(step.quiz, step.session, 1, 1, random)
        assertEquals(PairOutcome.Ignored, again.outcome)
        assertEquals(step.session, again.session)
    }
}

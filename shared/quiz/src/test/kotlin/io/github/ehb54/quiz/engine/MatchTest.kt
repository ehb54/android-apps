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

    /** Pairs every tile of the block on screen, fillers too, in card order: the block's last pair is its highest card. */
    private fun MatchStep.clearBlock(): MatchStep {
        var step = this
        for (id in session.left.sorted()) step = pairTiles(step.quiz, step.session, id, id, random)
        return step
    }

    private fun start(quiz: Quiz) = MatchStep(quiz, startMatch(quiz, random)!!, PairOutcome.Paired)

    @Test
    fun cardsComeInBlocks() {
        var step = start(quiz(24))
        val blocks = mutableListOf<List<Long>>()
        while (!step.session.finished) {
            blocks += step.session.scored
            assertEquals(step.session.left.toSet(), step.session.right.toSet())
            assertTrue(step.session.right.containsAll(step.session.scored))
            step = step.clearBlock()
        }
        // Each block's last pair (its highest card) comes first in the next; card 24
        // is last in a block of fillers too, the only card it scores, and counts.
        assertEquals(
            listOf((1L..6L).toList(), (6L..11L).toList(), (11L..16L).toList(), (16L..21L).toList(), (21L..24L).toList(), listOf(24L)),
            blocks,
        )
        assertEquals(24, step.session.firstTry)
        assertEquals(1, step.session.round)
        step.quiz.cards.forEach {
            assertEquals(Stats(asked = 1, rightFirst = 1), it.statsMatch)
            assertEquals(Stats(), it.statsAToB)
        }
    }

    @Test
    fun aBlocksLastPairIsNotScoredAndComesFirstInTheNextBlock() {
        var step = start(quiz(12))
        step = step.clearBlock()
        assertEquals(PairOutcome.BlockCleared, step.outcome)
        assertEquals(listOf(6L, 7L, 8L, 9L, 10L, 11L), step.session.scored)
        assertEquals(Stats(), step.quiz.card(6)!!.statsMatch)
        assertEquals(Stats(asked = 1, rightFirst = 1), step.quiz.card(5)!!.statsMatch)
        assertEquals(5, step.session.firstTry)
        assertEquals(7, step.session.remaining)

        // Paired while other tiles were open, it counts, right the first time.
        step = pairTiles(step.quiz, step.session, 6, 6, random)
        step = step.clearBlock()
        assertEquals(Stats(asked = 1, rightFirst = 1), step.quiz.card(6)!!.statsMatch)
        assertEquals(listOf(11L, 12L), step.session.scored)
    }

    @Test
    fun aLastPairThatIsAllTheBlockScoresCounts() {
        // Three cards and no fillers: card 3, paired last, comes back among
        // cards 1 and 2 as fillers, and paired last again it counts.
        var step = start(quiz(3)).clearBlock()
        assertEquals(PairOutcome.BlockCleared, step.outcome)
        assertEquals(listOf(3L), step.session.scored)
        assertEquals(listOf(1L, 2L, 3L), step.session.left.sorted())
        assertEquals(1, step.session.remaining)

        step = step.clearBlock()
        assertEquals(PairOutcome.Finished, step.outcome)
        assertEquals(3, step.session.firstTry)
        assertEquals(Stats(asked = 1, rightFirst = 1), step.quiz.card(3)!!.statsMatch)
    }

    @Test
    fun aWrongPairIsAMissForBothCardsAndTheyComeBack() {
        var step = start(quiz(6))
        step = pairTiles(step.quiz, step.session, 1, 2, random)
        assertEquals(PairOutcome.Wrong, step.outcome)
        assertEquals(setOf(1L, 2L), step.session.missed)
        step = step.clearBlock()
        // Card 6 was the last pair: it comes back first, before round 2.
        assertEquals(1, step.session.round)
        assertEquals(listOf(6L), step.session.scored)
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
        assertEquals(listOf(6L, 7L, 8L), block.scored)
        assertEquals(6, block.left.size)
        val fillers = block.left - block.scored.toSet()
        assertTrue(fillers.all { it in 1L..5L })

        while (!step.session.finished) step = step.clearBlock()
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
    fun aPeekAtATileIsAMissForAScoredCardOnly() {
        var step = start(quiz(8)).clearBlock()
        val filler = (step.session.left - step.session.scored.toSet()).first()
        var session = peekTile(step.session, 7, sideA = false)
        assertEquals(setOf(7L), session.peekedRight)
        assertEquals(setOf(7L), session.missed)
        assertEquals(1, session.misses)
        // Again, or on its other tile, costs nothing more; nor does a filler.
        session = peekTile(peekTile(session, 7, sideA = false), 7, sideA = true)
        session = peekTile(session, filler, sideA = true)
        assertEquals(setOf(7L, filler), session.peekedLeft)
        assertEquals(setOf(7L), session.missed)
        assertEquals(1, session.misses)

        // Card 7 is a miss at the block's end, and the next block shows no hints.
        step = MatchStep(step.quiz, session, PairOutcome.Paired).clearBlock()
        assertEquals(Stats(asked = 1, rightFirst = 0, wrong = 1), step.quiz.card(7)!!.statsMatch)
        assertEquals(emptySet<Long>(), step.session.peekedLeft + step.session.peekedRight)
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

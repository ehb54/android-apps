package io.github.ehb54.quiz.engine

import io.github.ehb54.quiz.model.Card
import io.github.ehb54.quiz.model.Quiz
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * A match session: the due cards are shown a block at a time as two columns of
 * tiles, side A on the left and side B on the right, each column shuffled, and
 * the player pairs them up. When a block is cleared the next one appears.
 *
 * The rules follow typed sessions. A card paired without ever being in a wrong
 * pair is right the first time; a wrong pair is a miss for both of its cards
 * (which covers the confusion penalty). Missed cards come back in the next
 * round, a block at a time, until a round has no misses. A block short of cards
 * is topped up with fillers: cards not still to be asked, which are not scored.
 * Matching keeps its own level per card ([Card.statsMatch]).
 * Saved after every pair, so it survives the app being closed.
 */
@Serializable
data class MatchSession(
    val quizId: String,
    val blockSize: Int,
    val total: Int,
    val round: Int = 1,
    /** Cards still to clear this round, after the block on screen. */
    val pending: List<Long>,
    /** Cards missed this round, asked again next round. */
    val retry: List<Long> = emptyList(),
    /** Cards of the block on screen that count; its other tiles are fillers. */
    val scored: List<Long> = emptyList(),
    /** The block's side A tiles, top to bottom, by card. */
    val left: List<Long> = emptyList(),
    /** The block's side B tiles, top to bottom, by card. */
    val right: List<Long> = emptyList(),
    val matchedLeft: Set<Long> = emptySet(),
    val matchedRight: Set<Long> = emptySet(),
    /** Scored cards of this block that have been in a wrong pair. */
    val missed: Set<Long> = emptySet(),
    /** Cards cleared at the first try in round 1. */
    val firstTry: Int = 0,
    /** Wrong pairs. */
    val misses: Int = 0,
) {
    val finished: Boolean get() = left.isEmpty()

    /** Cards still to clear: the block's unpaired scored cards, the rest of the round, and the misses. */
    val remaining: Int get() = scored.count { it !in matchedLeft } + pending.size + retry.size
}

enum class PairOutcome { Paired, Wrong, BlockCleared, Finished, Ignored }

class MatchStep(val quiz: Quiz, val session: MatchSession, val outcome: PairOutcome)

/** Starts a match session on the quiz's due cards (its settings in match mode), or null when none are due. */
fun startMatch(quiz: Quiz, random: Random = Random.Default): MatchSession? {
    val ids = quiz.sessionCards().map { it.id }
    if (ids.isEmpty()) return null
    val order = if (quiz.settings.shuffle) ids.shuffled(random) else ids
    val session = MatchSession(
        quizId = quiz.id,
        blockSize = quiz.settings.blockSize.coerceIn(2, 20),
        total = ids.size,
        pending = order,
    )
    return nextBlock(quiz, session, random)
}

/** Pairs the side A tile of card [left] with the side B tile of card [right]. */
fun pairTiles(quiz: Quiz, session: MatchSession, left: Long, right: Long, random: Random = Random.Default): MatchStep {
    val open = left in session.left && right in session.right &&
        left !in session.matchedLeft && right !in session.matchedRight
    if (!open) return MatchStep(quiz, session, PairOutcome.Ignored)

    if (!belongTogether(quiz.card(left), quiz.card(right))) {
        val missed = session.missed + listOf(left, right).filter { it in session.scored }
        return MatchStep(quiz, session.copy(missed = missed, misses = session.misses + 1), PairOutcome.Wrong)
    }
    val paired = session.copy(matchedLeft = session.matchedLeft + left, matchedRight = session.matchedRight + right)
    if (paired.matchedLeft.size < paired.left.size) return MatchStep(quiz, paired, PairOutcome.Paired)

    // Block cleared: record each scored card's result, then show the next block.
    var scoredQuiz = quiz
    var firstTry = paired.firstTry
    for (id in paired.scored) {
        val card = scoredQuiz.card(id) ?: continue
        val missed = id in paired.missed
        val stats = if (missed) card.statsMatch.answeredAfterMiss() else card.statsMatch.answeredFirstTry()
        scoredQuiz = scoredQuiz.replaceCard(card.copy(statsMatch = stats))
        if (!missed && paired.round == 1) firstTry++
    }
    val cleared = paired.copy(retry = paired.retry + paired.scored.filter { it in paired.missed }, firstTry = firstTry)
    val next = nextBlock(scoredQuiz, cleared, random)
    return MatchStep(scoredQuiz, next, if (next.finished) PairOutcome.Finished else PairOutcome.BlockCleared)
}

/** The next block from the round's pending cards, or the next round's; empty when the session is over. */
private fun nextBlock(quiz: Quiz, session: MatchSession, random: Random): MatchSession {
    var s = session
    if (s.pending.isEmpty() && s.retry.isNotEmpty()) {
        s = s.copy(pending = s.retry, retry = emptyList(), round = s.round + 1)
    }
    val scored = s.pending.take(s.blockSize)
    val stillToAsk = (s.pending + s.retry).toSet()
    val fillers = if (scored.isEmpty()) {
        emptyList()
    } else {
        quiz.cards.filter { it.matchable() && it.id !in stillToAsk }.map { it.id }
            .shuffled(random).take(s.blockSize - scored.size)
    }
    val tiles = scored + fillers
    return s.copy(
        pending = s.pending.drop(scored.size),
        scored = scored,
        left = tiles.shuffled(random),
        right = tiles.shuffled(random),
        matchedLeft = emptySet(),
        matchedRight = emptySet(),
        missed = emptySet(),
    )
}

/** Tiles belong together when they are the same card's, or when two cards show the same thing. */
private fun belongTogether(a: Card?, b: Card?): Boolean {
    if (a == null || b == null) return false
    return a.id == b.id || sameSide(a.b, a.imageB, b.b, b.imageB) || sameSide(a.a, a.imageA, b.a, b.imageA)
}

private fun sameSide(text: String, image: String?, otherText: String, otherImage: String?) =
    if (image != null || otherImage != null) image == otherImage else answersMatch(text, otherText)

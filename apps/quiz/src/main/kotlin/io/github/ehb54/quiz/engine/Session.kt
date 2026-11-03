package io.github.ehb54.quiz.engine

import io.github.ehb54.quiz.model.Card
import io.github.ehb54.quiz.model.Direction
import io.github.ehb54.quiz.model.Quiz
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** Response times over every attempt in a session. */
@Serializable
data class AnswerTimes(
    val count: Int = 0,
    val totalMillis: Long = 0,
    val minMillis: Long = 0,
    val maxMillis: Long = 0,
) {
    fun plus(millis: Long) = AnswerTimes(
        count = count + 1,
        totalMillis = totalMillis + millis,
        minMillis = if (count == 0) millis else minOf(minMillis, millis),
        maxMillis = maxOf(maxMillis, millis),
    )

    val averageMillis: Long get() = if (count == 0) 0 else totalMillis / count
}

/**
 * A quiz session, following the original program: the due cards are asked in
 * order, round after round. A card stays on screen until answered right; any
 * miss on it brings it back next round, and the session ends after a round
 * with no misses. Saved after every answer, so it survives the app closing.
 */
@Serializable
data class Session(
    val quizId: String,
    val direction: Direction,
    val timerSeconds: Int,
    val total: Int,
    /** Cards still to ask this round; the first one is on screen. */
    val queue: List<Long>,
    /** Cards missed this round, asked again next round. */
    val retry: List<Long> = emptyList(),
    val round: Int = 1,
    val missesOnCurrent: Int = 0,
    /** Wrong answers typed (normalized), for the confusion penalty at the end. */
    val wrongAnswers: Set<String> = emptySet(),
    /** Cards right on the first try in round 1. */
    val firstTry: Int = 0,
    val misses: Int = 0,
    val times: AnswerTimes = AnswerTimes(),
) {
    val current: Long? get() = queue.firstOrNull()
    val finished: Boolean get() = queue.isEmpty()
    val remaining: Int get() = queue.size + retry.size
}

enum class Outcome { Right, Wrong, TooSlow }

/** The result of one answer: the updated quiz and session, plus cards penalized at the end. */
data class Step(
    val quiz: Quiz,
    val session: Session,
    val outcome: Outcome,
    val confused: List<Card> = emptyList(),
)

/** Starts a session on the quiz's due cards, or returns null when nothing is due. */
fun startSession(quiz: Quiz, random: Random = Random.Default): Session? {
    val ids = quiz.due().map { it.id }
    if (ids.isEmpty()) return null
    val settings = quiz.settings
    return Session(
        quizId = quiz.id,
        direction = settings.direction,
        timerSeconds = settings.timerSeconds,
        total = ids.size,
        queue = if (settings.shuffle) ids.shuffled(random) else ids,
    )
}

/** Showing the answer counts as a miss, as typing "!" did in the original. */
fun revealAnswer(session: Session) =
    session.copy(missesOnCurrent = session.missesOnCurrent + 1, misses = session.misses + 1)

/**
 * Checks [typed] against the current card. A slow answer (over the timer) is a
 * miss even when right. A right answer records the card's result and moves on.
 */
fun submitAnswer(quiz: Quiz, session: Session, typed: String, elapsedMillis: Long): Step {
    val id = session.current ?: return Step(quiz, session, Outcome.Right)
    val card = quiz.card(id) ?: return advance(quiz, session.copy(missesOnCurrent = 0), id, missed = false)
    val timed = session.copy(times = session.times.plus(elapsedMillis))

    if (session.timerSeconds > 0 && elapsedMillis > session.timerSeconds * 1000L) {
        return Step(quiz, timed.missedOnce(), Outcome.TooSlow)
    }
    val direction = session.direction
    if (!answersMatch(typed, card.answer(direction))) {
        val normalized = normalizeAnswer(typed)
        val wrongAnswers = if (normalized.isEmpty()) timed.wrongAnswers else timed.wrongAnswers + normalized
        return Step(quiz, timed.missedOnce().copy(wrongAnswers = wrongAnswers), Outcome.Wrong)
    }

    val missed = session.missesOnCurrent > 0
    val stats = card.stats(direction).let { if (missed) it.answeredAfterMiss() else it.answeredFirstTry() }
    val updated = quiz.replaceCard(card.withStats(direction, stats))
    val counted = if (!missed && session.round == 1) timed.copy(firstTry = timed.firstTry + 1) else timed
    return advance(updated, counted.copy(missesOnCurrent = 0), id, missed)
}

private fun Session.missedOnce() = copy(missesOnCurrent = missesOnCurrent + 1, misses = misses + 1)

private fun advance(quiz: Quiz, session: Session, id: Long, missed: Boolean): Step {
    var next = session.copy(
        queue = session.queue.drop(1),
        retry = if (missed) session.retry + id else session.retry,
    )
    if (next.queue.isEmpty() && next.retry.isNotEmpty()) {
        next = next.copy(queue = next.retry, retry = emptyList(), round = next.round + 1)
    }
    if (!next.finished) return Step(quiz, next, Outcome.Right)

    // Session over: any card whose answer was typed for a different card gets a miss too.
    val confused = quiz.cards.filter {
        it.askable(next.direction) && normalizeAnswer(it.answer(next.direction)) in next.wrongAnswers
    }
    val penalized = confused.fold(quiz) { q, card ->
        q.replaceCard(card.withStats(next.direction, card.stats(next.direction).confused()))
    }
    return Step(penalized, next, Outcome.Right, confused)
}

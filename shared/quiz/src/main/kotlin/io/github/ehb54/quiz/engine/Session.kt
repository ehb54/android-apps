package io.github.ehb54.quiz.engine

import io.github.ehb54.quiz.model.Card
import io.github.ehb54.quiz.model.Direction
import io.github.ehb54.quiz.model.Quiz
import io.github.ehb54.quiz.model.Stats
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
 *
 * The same session runs pick mode ([picking]): the answer is picked from
 * [choices] instead of typed, a wrong choice is ruled out until the card is
 * answered, and the cards whose answers were picked wrongly get the confusion
 * penalty. Pick mode keeps its own statistics ([Card.pickStats]).
 *
 * In speed mode ([speed]) a session is one level of a run that does not end:
 * when it is done, the next level starts (see engine/Speed.kt).
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
    /** Pick mode: choices per card; 0 for a typed session. */
    val choiceCount: Int = 0,
    /** Pick mode: the current card's choices, by card, in screen order. */
    val choices: List<Long> = emptyList(),
    /** Pick mode: choices already tried for the current card. */
    val ruledOut: Set<Long> = emptySet(),
    /** Pick mode: cards whose answer was picked for a different card, for the confusion penalty. */
    val wrongPicks: Set<Long> = emptySet(),
    /** Speed mode: the run this session is a level of; null in other modes. */
    val speed: SpeedLevel? = null,
) {
    val current: Long? get() = queue.firstOrNull()
    val finished: Boolean get() = queue.isEmpty()
    val remaining: Int get() = queue.size + retry.size
    val picking: Boolean get() = choiceCount > 0

    /** The time limit for an answer, 0 for none: the timer, or in speed mode the level's limit. */
    val limitMillis: Long get() = speed?.limitMillis ?: (timerSeconds * 1000L)
}

/** [Ignored]: a pick of a choice that is not on screen, or already ruled out. */
enum class Outcome { Right, Wrong, TooSlow, Ignored }

/** The result of one answer: the updated quiz and session, plus cards penalized at the end. */
data class Step(
    val quiz: Quiz,
    val session: Session,
    val outcome: Outcome,
    val confused: List<Card> = emptyList(),
)

/** Starts a session on the quiz's session cards (see [Quiz.sessionCards]), or null when none are due. */
fun startSession(quiz: Quiz, random: Random = Random.Default): Session? {
    val ids = quiz.sessionCards().map { it.id }
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

    if (session.limitMillis > 0 && elapsedMillis > session.limitMillis) {
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

/** Starts a pick-mode session: like [startSession], each card offering [QuizSettings.choices] choices. */
fun startPickSession(quiz: Quiz, random: Random = Random.Default): Session? {
    val session = startSession(quiz, random) ?: return null
    return session.copy(choiceCount = quiz.settings.choices.coerceIn(2, 8)).withChoices(quiz, random)
}

/**
 * Pick mode: checks the choice [picked] (a card) for the current card. A slow
 * pick is a miss even when right; a wrong one is a miss, is ruled out, and its
 * card gets the confusion penalty at the end. A right pick moves on.
 */
fun pickAnswer(quiz: Quiz, session: Session, picked: Long, elapsedMillis: Long, random: Random = Random.Default): Step {
    val id = session.current ?: return Step(quiz, session, Outcome.Ignored)
    if (picked !in session.choices || picked in session.ruledOut) return Step(quiz, session, Outcome.Ignored)
    val card = quiz.card(id) ?: return advance(quiz, session.copy(missesOnCurrent = 0), id, missed = false, random)
    val timed = session.copy(times = session.times.plus(elapsedMillis))

    if (session.limitMillis > 0 && elapsedMillis > session.limitMillis) {
        return Step(quiz, timed.missedOnce(), Outcome.TooSlow)
    }
    if (picked != id) {
        val wrong = timed.missedOnce().copy(ruledOut = timed.ruledOut + picked, wrongPicks = timed.wrongPicks + picked)
        return Step(quiz, wrong, Outcome.Wrong)
    }

    val missed = session.missesOnCurrent > 0
    val stats = card.picked(session).let { if (missed) it.answeredAfterMiss() else it.answeredFirstTry() }
    var answered = card.withPicked(session, stats)
    var counted = if (!missed && session.round == 1) timed.copy(firstTry = timed.firstTry + 1) else timed
    // Speed mode times right first picks of the level's first round: the card's latest, and the level's.
    val speed = session.speed
    if (speed != null && !missed && session.round == 1) {
        answered = answered.withSpeedMillis(session.direction, elapsedMillis)
        counted = counted.copy(speed = speed.copy(times = speed.times + (id to elapsedMillis)))
    }
    return advance(quiz.replaceCard(answered), counted.copy(missesOnCurrent = 0), id, missed, random)
}

/** The record a pick goes to: speed mode's or pick mode's, in the session's direction. */
private fun Card.picked(session: Session) =
    if (session.speed != null) speedStats(session.direction) else pickStats(session.direction)

private fun Card.withPicked(session: Session, stats: Stats) =
    if (session.speed != null) withSpeedStats(session.direction, stats) else withPickStats(session.direction, stats)

/**
 * The choices for [card]: its own answer and up to [count] - 1 other cards'
 * answers, in random order, no two showing the same thing (so exactly one is right).
 */
internal fun pickChoices(quiz: Quiz, card: Card, direction: Direction, count: Int, random: Random): List<Long> {
    val shown = mutableSetOf(answerKey(card, direction))
    val others = quiz.cards.filter { it.id != card.id && it.matchable() }
        .shuffled(random)
        .filter { shown.add(answerKey(it, direction)) }
        .take(count - 1)
    return (others.map { it.id } + card.id).shuffled(random)
}

private fun answerKey(card: Card, direction: Direction) =
    card.answerMedia(direction)?.let { "file:$it" } ?: normalizeAnswer(card.answer(direction))

internal fun Session.withChoices(quiz: Quiz, random: Random): Session {
    val card = current?.let(quiz::card) ?: return copy(choices = emptyList(), ruledOut = emptySet())
    return copy(choices = pickChoices(quiz, card, direction, choiceCount, random), ruledOut = emptySet())
}

private fun Session.missedOnce() = copy(missesOnCurrent = missesOnCurrent + 1, misses = misses + 1)

private fun advance(quiz: Quiz, session: Session, id: Long, missed: Boolean, random: Random = Random.Default): Step {
    var next = session.copy(
        queue = session.queue.drop(1),
        retry = if (missed) session.retry + id else session.retry,
    )
    if (next.queue.isEmpty() && next.retry.isNotEmpty()) {
        next = next.copy(queue = next.retry, retry = emptyList(), round = next.round + 1)
    }
    if (!next.finished) return Step(quiz, if (next.picking) next.withChoices(quiz, random) else next, Outcome.Right)

    // Session over: any card whose answer was given for a different card gets a miss too.
    val direction = next.direction
    val confused = if (next.picking) {
        quiz.cards.filter { it.id in next.wrongPicks }
    } else {
        quiz.cards.filter { it.askable(direction) && normalizeAnswer(it.answer(direction)) in next.wrongAnswers }
    }
    val penalized = confused.fold(quiz) { q, card ->
        if (next.picking) {
            q.replaceCard(card.withPicked(next, card.picked(next).confused()))
        } else {
            q.replaceCard(card.withStats(direction, card.stats(direction).confused()))
        }
    }
    // Speed mode never ends here: the next level follows.
    if (next.speed != null) return Step(penalized, nextLevel(penalized, next, confused, random), Outcome.Right, confused)
    return Step(penalized, next, Outcome.Right, confused)
}

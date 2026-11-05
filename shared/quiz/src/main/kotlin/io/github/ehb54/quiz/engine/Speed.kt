package io.github.ehb54.quiz.engine

import io.github.ehb54.quiz.model.Card
import io.github.ehb54.quiz.model.Direction
import io.github.ehb54.quiz.model.Quiz
import io.github.ehb54.quiz.model.QuizMode
import kotlinx.serialization.Serializable
import kotlin.math.ceil
import kotlin.random.Random

// Speed mode: picking that goes on until you stop, for speed of recognition.
// A run takes a fixed set of cards and plays them a level at a time. Level N
// asks the cards whose speed level is below N, as a pick session would with
// threshold N; when it is done (every card picked right first time), the
// level's times are shown and level N + 1 follows. Each card keeps its own
// speed record (Card.statsSpeedAToB/BToA), so a new run starts where the
// last one stopped.

/** How much slower than the last level's median a pick may be when the time limit tightens. */
private const val TIGHTEN_FACTOR = 1.5

/** A card's time to its right first pick. */
@Serializable
data class CardTime(val card: Long, val millis: Long)

/**
 * One level's results, shown between levels. Times are of right first picks
 * in the level's first round; 0 when there were none. [previousAverageMillis]
 * and [previousMedianMillis] are the level before's, 0 in a run's first level.
 * [stopped]: the level was stopped before its end, after [answered] of [cards].
 */
@Serializable
data class LevelResult(
    val level: Int,
    val cards: Int,
    val answered: Int,
    val firstTry: Int,
    val misses: Int,
    val averageMillis: Long,
    val medianMillis: Long,
    val fastestMillis: Long,
    val slowestMillis: Long,
    /** The slowest cards, slowest first, at most three. */
    val slowest: List<CardTime>,
    /** Cards whose answers were picked for other cards: they got a miss too. */
    val confused: List<Long> = emptyList(),
    val previousAverageMillis: Long = 0,
    val previousMedianMillis: Long = 0,
    val stopped: Boolean = false,
)

/**
 * Where a speed run stands: the [level] being played, its fixed [cards], the
 * time limit for a pick ([limitMillis], 0 for none) and the level's [times] so
 * far. [previous] is the level before's result. While [shown] is set, its
 * results are on screen and the level waits for Go on (or the run has stopped).
 */
@Serializable
data class SpeedLevel(
    val level: Int,
    val cards: List<Long>,
    val limitMillis: Long = 0,
    val times: Map<Long, Long> = emptyMap(),
    val previous: LevelResult? = null,
    val shown: LevelResult? = null,
)

/** The cards a speed run plays: the range, or else the first cards-per-session cards speed mode can ask. */
fun Quiz.speedCards(): List<Card> {
    val cards = askable(QuizMode.Speed)
    return if (settings.hasRange || settings.sessionSize <= 0) cards else cards.take(settings.sessionSize)
}

/** The level a new run starts at: one above the lowest speed level among its cards, and at least 1. */
fun Quiz.speedStartLevel(): Int {
    val lowest = speedCards().minOfOrNull { it.speedStats(settings.direction).level } ?: 0
    return maxOf(lowest + 1, 1)
}

/** Starts a speed run at [Quiz.speedStartLevel], or null when the quiz has nothing speed mode can ask. */
fun startSpeedRun(quiz: Quiz, random: Random = Random.Default): Session? {
    val cards = quiz.speedCards()
    if (cards.isEmpty()) return null
    val speed = SpeedLevel(quiz.speedStartLevel(), cards.map { it.id }, limitMillis = quiz.settings.timerSeconds * 1000L)
    return levelSession(quiz, quiz.settings.direction, speed, random)
}

/** A session for [speed]'s level: the run's cards below it, in a new order when shuffling. */
private fun levelSession(quiz: Quiz, direction: Direction, speed: SpeedLevel, random: Random): Session {
    val ids = speed.cards.mapNotNull(quiz::card).filter { it.speedStats(direction).level < speed.level }.map { it.id }
    return Session(
        quizId = quiz.id,
        direction = direction,
        timerSeconds = quiz.settings.timerSeconds,
        total = ids.size,
        queue = if (quiz.settings.shuffle) ids.shuffled(random) else ids,
        choiceCount = quiz.settings.choices.coerceIn(2, 8),
        speed = speed,
    ).withChoices(quiz, random)
}

/** [session]'s level so far as results; [confused] cards got the confusion penalty at its end. */
fun levelResult(session: Session, confused: List<Card> = emptyList(), stopped: Boolean = false): LevelResult {
    val speed = requireNotNull(session.speed)
    val times = speed.times.values.sorted()
    return LevelResult(
        level = speed.level,
        cards = session.total,
        answered = session.total - session.remaining,
        firstTry = session.firstTry,
        misses = session.misses,
        averageMillis = if (times.isEmpty()) 0 else times.sum() / times.size,
        medianMillis = median(times),
        fastestMillis = times.firstOrNull() ?: 0,
        slowestMillis = times.lastOrNull() ?: 0,
        slowest = speed.times.entries.sortedByDescending { it.value }.take(3).map { CardTime(it.key, it.value) },
        confused = confused.map { it.id },
        previousAverageMillis = speed.previous?.averageMillis ?: 0,
        previousMedianMillis = speed.previous?.medianMillis ?: 0,
        stopped = stopped,
    )
}

/** The middle time, or the mean of the middle two; 0 for none. [sorted] must be in order. */
internal fun median(sorted: List<Long>): Long = when {
    sorted.isEmpty() -> 0
    sorted.size % 2 == 1 -> sorted[sorted.size / 2]
    else -> (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
}

/**
 * The level after [done]'s, waiting on the results screen: the run's cards
 * below the next level, with the time limit for it.
 */
internal fun nextLevel(quiz: Quiz, done: Session, confused: List<Card>, random: Random): Session {
    val speed = requireNotNull(done.speed)
    val result = levelResult(done, confused)
    val next = SpeedLevel(
        level = speed.level + 1,
        cards = speed.cards,
        limitMillis = nextLimit(quiz, speed.limitMillis, result),
        previous = result,
        shown = result,
    )
    return levelSession(quiz, done.direction, next, random)
}

/**
 * The time limit for the level after [result]: the timer's, or when the quiz
 * tightens it, [TIGHTEN_FACTOR] times the level's median, rounded up to a
 * tenth of a second and never above the timer. A level with no times keeps [current].
 */
internal fun nextLimit(quiz: Quiz, current: Long, result: LevelResult): Long {
    val timer = quiz.settings.timerSeconds * 1000L
    if (!quiz.settings.tighten) return timer
    if (result.medianMillis == 0L) return current
    val tightened = ceil(result.medianMillis * TIGHTEN_FACTOR / 100).toLong() * 100
    return if (timer > 0) minOf(timer, tightened) else tightened
}

/** Leaves the results screen for the level waiting behind it. */
fun goOn(session: Session): Session = session.copy(speed = session.speed?.copy(shown = null))

/** Stops the run in the middle of a level: its results so far go on screen, ending the run. */
fun stopLevel(session: Session): Session {
    val speed = session.speed ?: return session
    return session.copy(speed = speed.copy(shown = levelResult(session, stopped = true)))
}

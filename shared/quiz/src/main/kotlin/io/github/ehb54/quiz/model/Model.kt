package io.github.ehb54.quiz.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Which side is shown: A -> B shows side A and asks for side B. */
@Serializable
enum class Direction { AToB, BToA }

/**
 * Typed: one card at a time, type the other side. Pick: one card at a time,
 * pick the other side from a few choices. Match: pair up tiles of side A and side B.
 * Speed: picking without end, a level at a time, for speed of recognition.
 */
@Serializable
enum class QuizMode { Typed, Pick, Match, Speed }

/**
 * One card's record for one direction, as in the original quiz program.
 * [rightFirst] counts consecutive first-try rights and drops to 0 on any miss;
 * [wrong] counts misses, capped at [MAX_WRONG], and never decays.
 */
@Serializable
data class Stats(
    val asked: Int = 0,
    val rightFirst: Int = 0,
    val wrong: Int = 0,
) {
    /** A card is learned, and skipped, once its level reaches the quiz threshold. */
    val level: Int get() = rightFirst - wrong

    fun answeredFirstTry() = copy(asked = asked + 1, rightFirst = rightFirst + 1)

    fun answeredAfterMiss() = copy(asked = asked + 1, rightFirst = 0, wrong = minOf(wrong + 1, MAX_WRONG))

    /** Confusion penalty: this card's answer was typed for a different card. */
    fun confused() = copy(rightFirst = 0, wrong = minOf(wrong + 1, MAX_WRONG))

    companion object {
        const val MAX_WRONG = 3
    }
}

/**
 * A flash card. Each side is text or, when its media is set, a file in the
 * app's media folder: a picture or a sound ([mediaKind]). A file can only be
 * shown or played, never typed, so typing asks a card only in directions whose
 * answer is text.
 */
@Serializable
data class Card(
    val id: Long,
    val a: String = "",
    val b: String = "",
    // Saved as "imageA" and "imageB": sides held only pictures before sounds came.
    @SerialName("imageA") val mediaA: String? = null,
    @SerialName("imageB") val mediaB: String? = null,
    val statsAToB: Stats = Stats(),
    val statsBToA: Stats = Stats(),
    /** Match mode's own record: recognizing a pair is easier than typing, so it is kept apart. */
    val statsMatch: Stats = Stats(),
    /** Pick mode's own records, one per direction, kept apart for the same reason. */
    val statsPickAToB: Stats = Stats(),
    val statsPickBToA: Stats = Stats(),
    /** Speed mode's own records, one per direction, kept apart since its levels rise in one sitting. */
    val statsSpeedAToB: Stats = Stats(),
    val statsSpeedBToA: Stats = Stats(),
    /** Speed mode: the card's latest time to a right first pick, per direction; 0 before the first. */
    val speedMillisAToB: Long = 0,
    val speedMillisBToA: Long = 0,
) {
    fun prompt(direction: Direction) = if (direction == Direction.AToB) a else b

    fun promptMedia(direction: Direction) = if (direction == Direction.AToB) mediaA else mediaB

    fun answer(direction: Direction) = if (direction == Direction.AToB) b else a

    fun answerMedia(direction: Direction) = if (direction == Direction.AToB) mediaB else mediaA

    fun askable(direction: Direction) = answerMedia(direction) == null && answer(direction).isNotBlank()

    fun stats(direction: Direction) = if (direction == Direction.AToB) statsAToB else statsBToA

    fun withStats(direction: Direction, stats: Stats) =
        if (direction == Direction.AToB) copy(statsAToB = stats) else copy(statsBToA = stats)

    /** Both sides show something (text, a picture or a sound), so the card can be a pair of tiles. */
    fun matchable() = (a.isNotBlank() || mediaA != null) && (b.isNotBlank() || mediaB != null)

    fun pickStats(direction: Direction) = if (direction == Direction.AToB) statsPickAToB else statsPickBToA

    fun withPickStats(direction: Direction, stats: Stats) =
        if (direction == Direction.AToB) copy(statsPickAToB = stats) else copy(statsPickBToA = stats)

    fun speedStats(direction: Direction) = if (direction == Direction.AToB) statsSpeedAToB else statsSpeedBToA

    fun withSpeedStats(direction: Direction, stats: Stats) =
        if (direction == Direction.AToB) copy(statsSpeedAToB = stats) else copy(statsSpeedBToA = stats)

    fun speedMillis(direction: Direction) = if (direction == Direction.AToB) speedMillisAToB else speedMillisBToA

    fun withSpeedMillis(direction: Direction, millis: Long) =
        if (direction == Direction.AToB) copy(speedMillisAToB = millis) else copy(speedMillisBToA = millis)

    /**
     * Whether [mode] can ask this card in [direction]. Typing needs a text answer. Picking
     * (Pick and Speed) and matching need both sides, but the answer can be a picture or a sound, since it is not typed.
     */
    fun canAsk(mode: QuizMode, direction: Direction) =
        if (mode == QuizMode.Typed) askable(direction) else matchable()

    /** This card's level in [mode] and [direction]; Match keeps one record for both ways. */
    fun level(mode: QuizMode, direction: Direction) = when (mode) {
        QuizMode.Typed -> stats(direction).level
        QuizMode.Pick -> pickStats(direction).level
        QuizMode.Match -> statsMatch.level
        QuizMode.Speed -> speedStats(direction).level
    }

    fun withoutStats() = copy(
        statsAToB = Stats(),
        statsBToA = Stats(),
        statsMatch = Stats(),
        statsPickAToB = Stats(),
        statsPickBToA = Stats(),
        statsSpeedAToB = Stats(),
        statsSpeedBToA = Stats(),
        speedMillisAToB = 0,
        speedMillisBToA = 0,
    )
}

/**
 * How the next session runs; remembered per quiz. Range is 1-based and
 * inclusive. Without a range, a session takes at most [sessionSize] due cards
 * (0 = all), so starting a long quiz by accident is not a 300-card session.
 */
@Serializable
data class QuizSettings(
    val direction: Direction = Direction.AToB,
    val threshold: Int = 1,
    val shuffle: Boolean = true,
    val timerSeconds: Int = 0,
    val rangeFrom: Int? = null,
    val rangeTo: Int? = null,
    val sessionSize: Int = DEFAULT_SESSION_SIZE,
    val mode: QuizMode = QuizMode.Typed,
    /** Match mode: pairs on screen at a time. */
    val blockSize: Int = DEFAULT_BLOCK_SIZE,
    /** Pick and Speed modes: choices per card, the right one included. */
    val choices: Int = DEFAULT_CHOICES,
    /** Speed mode: each level's time limit follows the level before (see engine/Speed.kt). */
    val tighten: Boolean = false,
) {
    val hasRange: Boolean get() = rangeFrom != null || rangeTo != null

    companion object {
        const val DEFAULT_SESSION_SIZE = 20
        const val DEFAULT_BLOCK_SIZE = 6
        const val DEFAULT_CHOICES = 4
    }
}

/**
 * Where one mode and direction stands: the [cards] it can ask, how many are [due],
 * and the [lowest] level among them (the level every one has reached; null without cards).
 */
data class Progress(val cards: Int, val due: Int, val lowest: Int?)

/**
 * A quiz. [source] and [sourceVersion] are set for quizzes added from the
 * app's samples: which sample (file, and tab for workbooks) and a fingerprint
 * of its contents, so samples are not added twice and updates can be offered.
 */
@Serializable
data class Quiz(
    val id: String,
    val name: String,
    val cards: List<Card> = emptyList(),
    val nextCardId: Long = 1,
    val settings: QuizSettings = QuizSettings(),
    val source: String? = null,
    val sourceVersion: String? = null,
) {
    fun card(id: Long) = cards.find { it.id == id }

    fun addCard(a: String, b: String, mediaA: String? = null, mediaB: String? = null) = copy(
        cards = cards + Card(nextCardId, a, b, mediaA, mediaB),
        nextCardId = nextCardId + 1,
    )

    fun replaceCard(card: Card) = copy(cards = cards.map { if (it.id == card.id) card else it })

    fun removeCard(id: Long) = copy(cards = cards.filterNot { it.id == id })

    fun resetStats() = copy(cards = cards.map { it.withoutStats() })

    /** The cards a session draws from: the settings' range, by position in the list. */
    fun cardsInRange(): List<Card> {
        if (cards.isEmpty()) return emptyList()
        val from = (settings.rangeFrom ?: 1).coerceIn(1, cards.size)
        val to = (settings.rangeTo ?: cards.size).coerceIn(from, cards.size)
        return cards.subList(from - 1, to)
    }

    /**
     * Cards below the threshold for [mode] (and [direction], when typed or picked),
     * by default the selected ones. The range applies; the cards-per-session cap does not.
     */
    fun due(mode: QuizMode = settings.mode, direction: Direction = settings.direction): List<Card> =
        askable(mode, direction).filter { isDue(it.level(mode, direction)) }

    /** Cards in range that [mode] can ask in [direction]. */
    fun askable(mode: QuizMode = settings.mode, direction: Direction = settings.direction) =
        cardsInRange().filter { it.canAsk(mode, direction) }

    /** Below the threshold; threshold 0 asks every card. */
    private fun isDue(level: Int) = settings.threshold == 0 || level < settings.threshold

    /** A card's level for the selected mode and direction, as the card list shows it. */
    fun level(card: Card) = card.level(settings.mode, settings.direction)

    /** How far [mode] has got in [direction], over the cards [askable] gives. */
    fun progress(mode: QuizMode = settings.mode, direction: Direction = settings.direction): Progress {
        val levels = askable(mode, direction).map { it.level(mode, direction) }
        return Progress(cards = levels.size, due = levels.count(::isDue), lowest = levels.minOrNull())
    }

    /** How many of the [askable] cards are at each level, lowest level first; levels with no cards are left out. */
    fun levels(mode: QuizMode = settings.mode, direction: Direction = settings.direction): Map<Int, Int> =
        askable(mode, direction).groupingBy { it.level(mode, direction) }.eachCount().toSortedMap()

    /**
     * The cards the next session asks: with a range, every due card in it;
     * without one, the first [QuizSettings.sessionSize] due cards in list order.
     */
    fun sessionCards(): List<Card> {
        val due = due()
        return if (settings.hasRange || settings.sessionSize <= 0) due else due.take(settings.sessionSize)
    }

    fun mediaFiles(): Set<String> = cards.flatMap { listOfNotNull(it.mediaA, it.mediaB) }.toSet()

    /**
     * This quiz with [fresh]'s cards, e.g. a newer version of a sample. Keeps
     * the name, settings and statistics of cards whose sides did not change.
     */
    fun replacedBy(fresh: Quiz): Quiz {
        val previous = cards.associateBy { it.contentKey() }
        val replaced = fresh.cards.mapIndexed { index, card ->
            val old = previous[card.contentKey()]
            card.copy(
                id = index + 1L,
                statsAToB = old?.statsAToB ?: Stats(),
                statsBToA = old?.statsBToA ?: Stats(),
                statsMatch = old?.statsMatch ?: Stats(),
                statsPickAToB = old?.statsPickAToB ?: Stats(),
                statsPickBToA = old?.statsPickBToA ?: Stats(),
                statsSpeedAToB = old?.statsSpeedAToB ?: Stats(),
                statsSpeedBToA = old?.statsSpeedBToA ?: Stats(),
                speedMillisAToB = old?.speedMillisAToB ?: 0,
                speedMillisBToA = old?.speedMillisBToA ?: 0,
            )
        }
        return copy(
            cards = replaced,
            nextCardId = replaced.size + 1L,
            source = fresh.source,
            sourceVersion = fresh.sourceVersion,
        )
    }
}

/** What a card says; a stored picture (its file name is random) counts only as "a picture". */
private fun Card.contentKey() =
    listOf(mediaA != null, a.takeIf { mediaA == null }, mediaB != null, b.takeIf { mediaB == null })

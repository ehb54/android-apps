package io.github.ehb54.quiz.model

import kotlinx.serialization.Serializable

/** Which side is shown: A -> B shows side A and asks for side B. */
@Serializable
enum class Direction { AToB, BToA }

/**
 * Typed: one card at a time, type the other side. Pick: one card at a time,
 * pick the other side from a few choices. Match: pair up tiles of side A and side B.
 */
@Serializable
enum class QuizMode { Typed, Pick, Match }

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
 * A flash card. Each side is text or, when its image is set, a picture
 * (a file name in the app's image folder). A picture can only be shown,
 * never typed, so a card is asked only in directions whose answer is text.
 */
@Serializable
data class Card(
    val id: Long,
    val a: String = "",
    val b: String = "",
    val imageA: String? = null,
    val imageB: String? = null,
    val statsAToB: Stats = Stats(),
    val statsBToA: Stats = Stats(),
    /** Match mode's own record: recognizing a pair is easier than typing, so it is kept apart. */
    val statsMatch: Stats = Stats(),
    /** Pick mode's own records, one per direction, kept apart for the same reason. */
    val statsPickAToB: Stats = Stats(),
    val statsPickBToA: Stats = Stats(),
) {
    fun prompt(direction: Direction) = if (direction == Direction.AToB) a else b

    fun promptImage(direction: Direction) = if (direction == Direction.AToB) imageA else imageB

    fun answer(direction: Direction) = if (direction == Direction.AToB) b else a

    fun answerImage(direction: Direction) = if (direction == Direction.AToB) imageB else imageA

    fun askable(direction: Direction) = answerImage(direction) == null && answer(direction).isNotBlank()

    fun stats(direction: Direction) = if (direction == Direction.AToB) statsAToB else statsBToA

    fun withStats(direction: Direction, stats: Stats) =
        if (direction == Direction.AToB) copy(statsAToB = stats) else copy(statsBToA = stats)

    /** Due in a session: askable and below the threshold (threshold 0 asks every card). */
    fun isDue(direction: Direction, threshold: Int) =
        askable(direction) && (threshold == 0 || stats(direction).level < threshold)

    /** Both sides show something, text or picture, so the card can be a pair of tiles. */
    fun matchable() = (a.isNotBlank() || imageA != null) && (b.isNotBlank() || imageB != null)

    fun isDueForMatch(threshold: Int) = matchable() && (threshold == 0 || statsMatch.level < threshold)

    fun pickStats(direction: Direction) = if (direction == Direction.AToB) statsPickAToB else statsPickBToA

    fun withPickStats(direction: Direction, stats: Stats) =
        if (direction == Direction.AToB) copy(statsPickAToB = stats) else copy(statsPickBToA = stats)

    /** Pick mode needs both sides too: the answer can be a picture, since it is picked, not typed. */
    fun isDueForPick(direction: Direction, threshold: Int) =
        matchable() && (threshold == 0 || pickStats(direction).level < threshold)

    fun withoutStats() = copy(
        statsAToB = Stats(),
        statsBToA = Stats(),
        statsMatch = Stats(),
        statsPickAToB = Stats(),
        statsPickBToA = Stats(),
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
    /** Pick mode: choices per card, the right one included. */
    val choices: Int = DEFAULT_CHOICES,
) {
    val hasRange: Boolean get() = rangeFrom != null || rangeTo != null

    companion object {
        const val DEFAULT_SESSION_SIZE = 20
        const val DEFAULT_BLOCK_SIZE = 6
        const val DEFAULT_CHOICES = 4
    }
}

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

    fun addCard(a: String, b: String, imageA: String? = null, imageB: String? = null) = copy(
        cards = cards + Card(nextCardId, a, b, imageA, imageB),
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

    /** Cards below the threshold for the selected mode (and direction, when typed or picked). */
    fun due(): List<Card> = cardsInRange().filter {
        when (settings.mode) {
            QuizMode.Typed -> it.isDue(settings.direction, settings.threshold)
            QuizMode.Pick -> it.isDueForPick(settings.direction, settings.threshold)
            QuizMode.Match -> it.isDueForMatch(settings.threshold)
        }
    }

    /** A card's level for the selected mode, as the card list shows it. */
    fun level(card: Card) = when (settings.mode) {
        QuizMode.Typed -> card.stats(settings.direction).level
        QuizMode.Pick -> card.pickStats(settings.direction).level
        QuizMode.Match -> card.statsMatch.level
    }

    /**
     * The cards the next session asks: with a range, every due card in it;
     * without one, the first [QuizSettings.sessionSize] due cards in list order.
     */
    fun sessionCards(): List<Card> {
        val due = due()
        return if (settings.hasRange || settings.sessionSize <= 0) due else due.take(settings.sessionSize)
    }

    fun imageFiles(): Set<String> = cards.flatMap { listOfNotNull(it.imageA, it.imageB) }.toSet()

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
    listOf(imageA != null, a.takeIf { imageA == null }, imageB != null, b.takeIf { imageB == null })

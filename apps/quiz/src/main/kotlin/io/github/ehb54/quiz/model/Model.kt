package io.github.ehb54.quiz.model

import kotlinx.serialization.Serializable

/** Which side is shown: A -> B shows side A and asks for side B. */
@Serializable
enum class Direction { AToB, BToA }

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
) {
    fun prompt(direction: Direction) = if (direction == Direction.AToB) a else b

    fun promptImage(direction: Direction) = if (direction == Direction.AToB) imageA else imageB

    fun answer(direction: Direction) = if (direction == Direction.AToB) b else a

    private fun answerImage(direction: Direction) = if (direction == Direction.AToB) imageB else imageA

    fun askable(direction: Direction) = answerImage(direction) == null && answer(direction).isNotBlank()

    fun stats(direction: Direction) = if (direction == Direction.AToB) statsAToB else statsBToA

    fun withStats(direction: Direction, stats: Stats) =
        if (direction == Direction.AToB) copy(statsAToB = stats) else copy(statsBToA = stats)

    /** Due in a session: askable and below the threshold (threshold 0 asks every card). */
    fun isDue(direction: Direction, threshold: Int) =
        askable(direction) && (threshold == 0 || stats(direction).level < threshold)
}

/** How the next session runs; remembered per quiz. Range is 1-based and inclusive. */
@Serializable
data class QuizSettings(
    val direction: Direction = Direction.AToB,
    val threshold: Int = 1,
    val shuffle: Boolean = true,
    val timerSeconds: Int = 0,
    val rangeFrom: Int? = null,
    val rangeTo: Int? = null,
)

@Serializable
data class Quiz(
    val id: String,
    val name: String,
    val cards: List<Card> = emptyList(),
    val nextCardId: Long = 1,
    val settings: QuizSettings = QuizSettings(),
) {
    fun card(id: Long) = cards.find { it.id == id }

    fun addCard(a: String, b: String, imageA: String? = null, imageB: String? = null) = copy(
        cards = cards + Card(nextCardId, a, b, imageA, imageB),
        nextCardId = nextCardId + 1,
    )

    fun replaceCard(card: Card) = copy(cards = cards.map { if (it.id == card.id) card else it })

    fun removeCard(id: Long) = copy(cards = cards.filterNot { it.id == id })

    fun resetStats() = copy(cards = cards.map { it.copy(statsAToB = Stats(), statsBToA = Stats()) })

    /** The cards a session draws from: the settings' range, by position in the list. */
    fun cardsInRange(): List<Card> {
        if (cards.isEmpty()) return emptyList()
        val from = (settings.rangeFrom ?: 1).coerceIn(1, cards.size)
        val to = (settings.rangeTo ?: cards.size).coerceIn(from, cards.size)
        return cards.subList(from - 1, to)
    }

    fun due(): List<Card> = cardsInRange().filter { it.isDue(settings.direction, settings.threshold) }

    fun imageFiles(): Set<String> = cards.flatMap { listOfNotNull(it.imageA, it.imageB) }.toSet()
}

package io.github.ehb54.quiz.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DueTest {
    @Test
    fun eachModeAndDirectionCountsItsOwnLevels() {
        var quiz = Quiz("q", "test", settings = QuizSettings(rangeTo = 3))
            .addCard("a", "1").addCard("b", "2").addCard("c", "3").addCard("d", "4")
        val learned = Stats(rightFirst = 1)
        quiz = quiz.replaceCard(quiz.cards[0].copy(statsAToB = learned, statsPickBToA = learned))
        quiz = quiz.replaceCard(quiz.cards[1].copy(statsMatch = learned))

        // Card 4 is outside the range; each mode and direction sees only its own record.
        assertEquals(2, quiz.due(QuizMode.Typed, Direction.AToB).size)
        assertEquals(3, quiz.due(QuizMode.Typed, Direction.BToA).size)
        assertEquals(3, quiz.due(QuizMode.Pick, Direction.AToB).size)
        assertEquals(2, quiz.due(QuizMode.Pick, Direction.BToA).size)
        assertEquals(2, quiz.due(QuizMode.Match).size)
        // Without arguments: the selected mode and direction (typed A -> B).
        assertEquals(quiz.due(QuizMode.Typed, Direction.AToB), quiz.due())
    }

    @Test
    fun progressGivesTheLowestLevelAndLevelsCountTheCards() {
        var quiz = Quiz("q", "test", settings = QuizSettings(threshold = 2))
            .addCard("a", "1").addCard("b", "2").addCard("c", "3").addCard("d", "4")
        val levels = listOf(2, 3, 2, 5)
        quiz.cards.zip(levels).forEach { (card, level) ->
            quiz = quiz.replaceCard(card.copy(statsAToB = Stats(rightFirst = level)))
        }

        // Cleared at threshold 2: nothing due, every card at level 2 or more.
        assertEquals(Progress(cards = 4, due = 0, lowest = 2), quiz.progress(QuizMode.Typed, Direction.AToB))
        assertEquals(mapOf(2 to 2, 3 to 1, 5 to 1), quiz.levels(QuizMode.Typed, Direction.AToB))
        assertEquals(listOf(2, 3, 5), quiz.levels(QuizMode.Typed, Direction.AToB).keys.toList())

        // Level 3 next: the two cards at level 2 are due. A miss takes a card below 0.
        quiz = quiz.copy(settings = quiz.settings.copy(threshold = 3))
        quiz = quiz.replaceCard(quiz.cards[3].copy(statsAToB = Stats(wrong = 1)))
        assertEquals(Progress(cards = 4, due = 3, lowest = -1), quiz.progress(QuizMode.Typed, Direction.AToB))
        assertEquals(mapOf(-1 to 1, 2 to 2, 3 to 1), quiz.levels(QuizMode.Typed, Direction.AToB))

        // Untouched records: all at level 0.
        assertEquals(Progress(cards = 4, due = 4, lowest = 0), quiz.progress(QuizMode.Match))
        assertEquals(mapOf(0 to 4), quiz.levels(QuizMode.Pick, Direction.BToA))

        // The range applies, as it does to due().
        quiz = quiz.copy(settings = quiz.settings.copy(rangeFrom = 2, rangeTo = 3))
        assertEquals(Progress(cards = 2, due = 1, lowest = 2), quiz.progress(QuizMode.Typed, Direction.AToB))
        assertEquals(mapOf(2 to 1, 3 to 1), quiz.levels(QuizMode.Typed, Direction.AToB))
    }

    @Test
    fun aPictureAnswerIsLeftOutOfTypingButNotPickingOrMatching() {
        val quiz = Quiz("q", "test")
            .addCard("", "circle", imageA = "circle.png")
            .addCard("", "square", imageA = "square.png")

        // Side A is a picture: typing can ask A -> B (type the name) but not B -> A.
        assertEquals(Progress(cards = 2, due = 2, lowest = 0), quiz.progress(QuizMode.Typed, Direction.AToB))
        assertEquals(Progress(cards = 0, due = 0, lowest = null), quiz.progress(QuizMode.Typed, Direction.BToA))
        assertEquals(emptyMap<Int, Int>(), quiz.levels(QuizMode.Typed, Direction.BToA))
        // A picture can be picked or matched.
        assertEquals(2, quiz.progress(QuizMode.Pick, Direction.BToA).cards)
        assertEquals(2, quiz.progress(QuizMode.Match).cards)
    }
}

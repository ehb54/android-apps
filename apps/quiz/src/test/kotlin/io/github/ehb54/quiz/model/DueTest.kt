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
}

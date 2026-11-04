package io.github.ehb54.quiz.data

import io.github.ehb54.quiz.engine.startSession
import io.github.ehb54.quiz.model.Quiz
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class QuizStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun quizzesAndSessionSurviveARestart() {
        val root = folder.newFolder()
        val quiz = Quiz("q1", "French").addCard("cat", "chat").addCard("dog", "chien")
        QuizStore(root).apply {
            saveQuiz(quiz)
            saveSession(startSession(quiz))
        }

        val reopened = QuizStore(root)
        assertEquals(listOf(quiz), reopened.loadQuizzes().quizzes)
        assertEquals(2, reopened.loadSession()!!.total)

        reopened.saveSession(null)
        assertNull(reopened.loadSession())
        reopened.deleteQuiz("q1")
        assertTrue(reopened.loadQuizzes().quizzes.isEmpty())
    }

    @Test
    fun unreadableQuizIsReportedAndLeftAlone() {
        val root = folder.newFolder()
        val broken = File(root, "quizzes/bad.json").apply {
            parentFile?.mkdirs()
            writeText("{ not json")
        }
        val loaded = QuizStore(root).loadQuizzes()
        assertEquals(listOf(broken), loaded.unreadable)
        assertTrue(broken.exists())
    }

    @Test
    fun onlyUnusedPicturesAreRemoved() {
        val store = QuizStore(folder.newFolder())
        store.mediaDir.mkdirs()
        store.mediaFile("used.png").writeText("x")
        store.mediaFile("left-over.png").writeText("x")
        store.removeUnusedMedia(setOf("used.png"))
        assertTrue(store.mediaFile("used.png").exists())
        assertFalse(store.mediaFile("left-over.png").exists())
    }
}

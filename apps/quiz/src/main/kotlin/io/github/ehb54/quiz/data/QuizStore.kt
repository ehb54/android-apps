package io.github.ehb54.quiz.data

import io.github.ehb54.quiz.engine.MatchSession
import io.github.ehb54.quiz.engine.Session
import io.github.ehb54.quiz.model.Quiz
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Keeps quizzes as one JSON file each, the running session, and the card
 * pictures, all under [root] (the app's private files folder). Every write
 * goes to a temporary file first and is then renamed into place, so a crash
 * mid-write never leaves a half-written quiz.
 */
class QuizStore(root: File) {
    private val quizDir = File(root, "quizzes")
    private val sessionFile = File(root, "session.json")
    private val matchFile = File(root, "match.json")
    val imageDir = File(root, "images")

    private val json = Json { ignoreUnknownKeys = true }

    class Loaded(val quizzes: List<Quiz>, val unreadable: List<File>)

    fun loadQuizzes(): Loaded {
        val quizzes = mutableListOf<Quiz>()
        val unreadable = mutableListOf<File>()
        quizDir.listFiles { file -> file.name.endsWith(".json") }.orEmpty().forEach { file ->
            runCatching { json.decodeFromString(Quiz.serializer(), file.readText()) }
                .onSuccess { quizzes += it }
                .onFailure { unreadable += file }
        }
        return Loaded(quizzes.sortedBy { it.name.lowercase() }, unreadable)
    }

    fun saveQuiz(quiz: Quiz) =
        writeAtomically(File(quizDir, "${quiz.id}.json"), json.encodeToString(Quiz.serializer(), quiz))

    fun deleteQuiz(id: String) {
        File(quizDir, "$id.json").delete()
    }

    fun loadSession(): Session? {
        if (!sessionFile.exists()) return null
        return runCatching { json.decodeFromString(Session.serializer(), sessionFile.readText()) }.getOrNull()
    }

    fun saveSession(session: Session?) {
        if (session == null) {
            sessionFile.delete()
        } else {
            writeAtomically(sessionFile, json.encodeToString(Session.serializer(), session))
        }
    }

    /** The running match session, kept apart from a typed one (at most one of the two runs). */
    fun loadMatch(): MatchSession? {
        if (!matchFile.exists()) return null
        return runCatching { json.decodeFromString(MatchSession.serializer(), matchFile.readText()) }.getOrNull()
    }

    fun saveMatch(match: MatchSession?) {
        if (match == null) {
            matchFile.delete()
        } else {
            writeAtomically(matchFile, json.encodeToString(MatchSession.serializer(), match))
        }
    }

    fun imageFile(name: String) = File(imageDir, name)

    fun deleteImages(names: Collection<String>) {
        names.forEach { imageFile(it).delete() }
    }

    /** Deletes pictures no card uses (left by a cancelled edit). Only call when every quiz loaded. */
    fun removeUnusedImages(used: Set<String>) {
        imageDir.listFiles().orEmpty().filter { it.name !in used }.forEach { it.delete() }
    }

    private fun writeAtomically(target: File, text: String) {
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, "${target.name}.tmp")
        temporary.writeText(text)
        Files.move(
            temporary.toPath(),
            target.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }
}

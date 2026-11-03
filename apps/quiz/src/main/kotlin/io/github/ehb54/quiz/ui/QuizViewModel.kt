package io.github.ehb54.quiz.ui

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.ehb54.quiz.R
import io.github.ehb54.quiz.data.QuizStore
import io.github.ehb54.quiz.data.storeImage
import io.github.ehb54.quiz.engine.AnswerTimes
import io.github.ehb54.quiz.engine.IMAGE_MARK
import io.github.ehb54.quiz.engine.Outcome
import io.github.ehb54.quiz.engine.QzxCard
import io.github.ehb54.quiz.engine.Session
import io.github.ehb54.quiz.engine.cardsFromSheet
import io.github.ehb54.quiz.engine.formatQzx
import io.github.ehb54.quiz.engine.isXlsx
import io.github.ehb54.quiz.engine.isZip
import io.github.ehb54.quiz.engine.parseQzx
import io.github.ehb54.quiz.engine.quizBundle
import io.github.ehb54.quiz.engine.readLimited
import io.github.ehb54.quiz.engine.readXlsx
import io.github.ehb54.quiz.engine.revealAnswer
import io.github.ehb54.quiz.engine.startSession
import io.github.ehb54.quiz.engine.submitAnswer
import io.github.ehb54.quiz.engine.unzip
import io.github.ehb54.quiz.engine.writeZip
import io.github.ehb54.quiz.model.Card
import io.github.ehb54.quiz.model.Quiz
import io.github.ehb54.quiz.model.QuizSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.Executors

sealed interface Screen {
    data object Loading : Screen
    data object QuizList : Screen
    data class QuizDetail(val quizId: String) : Screen
    data object Asking : Screen
    data object Done : Screen
}

enum class Feedback { Wrong, TooSlow }

/** Folder in assets with the sample quiz files. */
private const val SAMPLES = "samples"

/** What the Done screen shows after a session. */
data class Summary(
    val quizId: String,
    val total: Int,
    val firstTry: Int,
    val misses: Int,
    val rounds: Int,
    val times: AnswerTimes,
    val confused: List<String>,
)

data class UiState(
    val quizzes: List<Quiz> = emptyList(),
    val screen: Screen = Screen.Loading,
    val session: Session? = null,
    val feedback: Feedback? = null,
    val revealed: Boolean = false,
    val summary: Summary? = null,
) {
    fun quiz(id: String?) = quizzes.find { it.id == id }
}

/**
 * One thread for all disk reads and writes, in order. It belongs to the
 * process, not to a view model, so a change made just before the app closes
 * still reaches the disk, and a new view model loads only after it has.
 */
private val diskThread = Executors.newSingleThreadExecutor { Thread(it, "quiz-disk").apply { isDaemon = true } }
private val diskDispatcher = diskThread.asCoroutineDispatcher()

/**
 * Holds the quizzes and the running session for the UI. State changes happen
 * on the main thread; each change is then saved on the disk thread, so the
 * session survives the app being closed at any point.
 */
class QuizViewModel(application: Application) : AndroidViewModel(application) {
    private val store = QuizStore(application.filesDir)
    val imageDir: File = store.imageDir

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val (loaded, session) = withContext(diskDispatcher) {
            val loaded = store.loadQuizzes()
            if (loaded.unreadable.isEmpty()) {
                store.removeUnusedImages(loaded.quizzes.flatMap { it.imageFiles() }.toSet())
            }
            loaded to store.loadSession()
        }
        val active = session?.takeIf { s -> !s.finished && loaded.quizzes.any { it.id == s.quizId } }
        _state.value = UiState(
            quizzes = loaded.quizzes,
            session = active,
            screen = if (active != null) Screen.Asking else Screen.QuizList,
        )
        if (loaded.unreadable.isNotEmpty()) message(plural(R.plurals.unreadable_files, loaded.unreadable.size))
    }

    // Navigation

    fun openQuiz(id: String) = _state.update { it.copy(screen = Screen.QuizDetail(id)) }

    fun showList() = _state.update { it.copy(screen = Screen.QuizList) }

    // Quizzes and cards

    fun createQuiz(name: String) {
        val quiz = Quiz(UUID.randomUUID().toString(), uniqueName(name.trim()))
        addQuiz(quiz)
        openQuiz(quiz.id)
    }

    fun renameQuiz(id: String, name: String) = changeQuiz(id) { it.copy(name = name.trim()) }

    fun resetStats(id: String) = changeQuiz(id) { it.resetStats() }

    fun updateSettings(id: String, settings: QuizSettings) = changeQuiz(id) { it.copy(settings = settings) }

    /** Saves an edited card, or adds it when its id is 0. */
    fun saveCard(quizId: String, card: Card) = changeQuiz(quizId) { quiz ->
        if (card.id == 0L) quiz.addCard(card.a, card.b, card.imageA, card.imageB) else quiz.replaceCard(card)
    }

    fun deleteCard(quizId: String, cardId: Long) = changeQuiz(quizId) { it.removeCard(cardId) }

    fun deleteQuiz(id: String) {
        val quiz = _state.value.quiz(id) ?: return
        _state.update { s -> s.copy(quizzes = s.quizzes.filterNot { it.id == id }, screen = Screen.QuizList) }
        persist {
            store.deleteQuiz(id)
            store.deleteImages(quiz.imageFiles())
        }
    }

    private fun addQuiz(quiz: Quiz) {
        _state.update { s -> s.copy(quizzes = (s.quizzes + quiz).sortedBy { it.name.lowercase() }) }
        persist { store.saveQuiz(quiz) }
    }

    private fun changeQuiz(id: String, change: (Quiz) -> Quiz) {
        val old = _state.value.quiz(id) ?: return
        val new = change(old)
        _state.update { s ->
            s.copy(quizzes = s.quizzes.map { if (it.id == id) new else it }.sortedBy { it.name.lowercase() })
        }
        val unused = old.imageFiles() - new.imageFiles()
        persist {
            store.saveQuiz(new)
            store.deleteImages(unused)
        }
    }

    private fun uniqueName(name: String): String {
        val taken = _state.value.quizzes.map { it.name }.toSet()
        if (name !in taken) return name
        return generateSequence(2) { it + 1 }.map { "$name $it" }.first { it !in taken }
    }

    // Asking

    fun startQuiz(id: String) {
        val quiz = _state.value.quiz(id) ?: return
        val session = startSession(quiz)
        if (session == null) {
            message(text(R.string.nothing_due))
            return
        }
        _state.update { it.copy(session = session, screen = Screen.Asking, feedback = null, revealed = false) }
        persist { store.saveSession(session) }
    }

    fun submit(typed: String, elapsedMillis: Long) {
        val state = _state.value
        val session = state.session ?: return
        val quiz = state.quiz(session.quizId) ?: return
        val step = submitAnswer(quiz, session, typed, elapsedMillis)
        val quizzes = state.quizzes.map { if (it.id == quiz.id) step.quiz else it }
        val next = step.session
        if (next.finished) {
            val summary = Summary(
                quizId = quiz.id,
                total = next.total,
                firstTry = next.firstTry,
                misses = next.misses,
                rounds = next.round,
                times = next.times,
                confused = step.confused.map { it.answer(next.direction) },
            )
            _state.value = state.copy(
                quizzes = quizzes,
                session = null,
                summary = summary,
                screen = Screen.Done,
                feedback = null,
                revealed = false,
            )
            persist {
                store.saveQuiz(step.quiz)
                store.saveSession(null)
            }
        } else {
            val feedback = when (step.outcome) {
                Outcome.Wrong -> Feedback.Wrong
                Outcome.TooSlow -> Feedback.TooSlow
                Outcome.Right -> null
            }
            _state.value = state.copy(quizzes = quizzes, session = next, feedback = feedback)
            persist {
                if (step.quiz !== quiz) store.saveQuiz(step.quiz)
                store.saveSession(next)
            }
        }
    }

    fun reveal() {
        val session = _state.value.session ?: return
        val next = revealAnswer(session)
        _state.update { it.copy(session = next, revealed = true, feedback = null) }
        persist { store.saveSession(next) }
    }

    fun hideAnswer() = _state.update { it.copy(revealed = false) }

    /** There is no leaving a session: Back only says how much is left. */
    fun backDuringQuiz() {
        val session = _state.value.session ?: return
        message(plural(R.plurals.finish_first, session.remaining))
    }

    fun closeSummary() = _state.update { s ->
        val id = s.summary?.quizId
        s.copy(summary = null, screen = if (s.quiz(id) != null) Screen.QuizDetail(id!!) else Screen.QuizList)
    }

    // Files

    /** Adds the sample quizzes shipped in assets/samples, through the same import code as files. */
    fun addSamples() {
        viewModelScope.launch {
            val assets = getApplication<Application>().assets
            val samples = withContext(Dispatchers.IO) {
                assets.list(SAMPLES).orEmpty().sorted().flatMap { file ->
                    runCatching { readQuizzes(file, assets.open("$SAMPLES/$file").use { readLimited(it) }) }
                        .getOrDefault(emptyList())
                }
            }
            samples.forEach { addQuiz(it.copy(name = uniqueName(it.name))) }
            message(plural(R.plurals.samples_added, samples.size))
        }
    }

    /** Imports a .qzx, a .zip with pictures, or an Excel workbook (one quiz per tab). */
    fun importQuiz(uri: Uri) {
        viewModelScope.launch {
            val quizzes = withContext(Dispatchers.IO) {
                runCatching { readQuizzes(getApplication<Application>().contentResolver, uri) }.getOrNull()
            }.orEmpty().filter { it.cards.isNotEmpty() }
            if (quizzes.isEmpty()) {
                message(text(R.string.import_failed))
                return@launch
            }
            val named = quizzes.map { it.copy(name = uniqueName(it.name)).also(::addQuiz) }
            if (named.size == 1) {
                openQuiz(named[0].id)
                message(plural(R.plurals.imported, named[0].cards.size))
            } else {
                message(plural(R.plurals.imported_quizzes, named.size))
            }
        }
    }

    fun exportQuiz(id: String, uri: Uri) {
        val quiz = _state.value.quiz(id) ?: return
        viewModelScope.launch {
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    val out = getApplication<Application>().contentResolver.openOutputStream(uri, "w")
                        ?: throw IOException("cannot open $uri")
                    out.use { writeQuiz(quiz, it) }
                }.isSuccess
            }
            message(text(if (written) R.string.exported else R.string.export_failed))
        }
    }

    /** Stores a picture chosen for a card side; [onStored] gets its file name, or null. */
    fun storePicture(uri: Uri, onStored: (String?) -> Unit) {
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.use { storeImage(store.imageDir, readLimited(it)) }
                }.getOrNull()
            }
            if (name == null) message(text(R.string.picture_failed))
            onStored(name)
        }
    }

    private fun readQuizzes(resolver: ContentResolver, uri: Uri): List<Quiz> {
        val fileName = displayName(resolver, uri) ?: text(R.string.imported_quiz)
        val bytes = resolver.openInputStream(uri)?.use { readLimited(it) } ?: throw IOException("cannot open $uri")
        return readQuizzes(fileName, bytes)
    }

    /**
     * New quizzes from a file: one named after [fileName] from .qzx text or a zip
     * with pictures, or one per non-empty tab, named after the tab, from an Excel workbook.
     */
    private fun readQuizzes(fileName: String, bytes: ByteArray): List<Quiz> {
        val name = fileName.substringBeforeLast('.').ifBlank { fileName }
        if (!isZip(bytes)) return listOf(newQuiz(name, parseQzx(bytes.toString(Charsets.UTF_8))))
        val entries = unzip(bytes.inputStream())
        if (isXlsx(entries)) {
            return readXlsx(entries).map { newQuiz(it.name, cardsFromSheet(it)) }.filter { it.cards.isNotEmpty() }
        }
        var quiz = newQuiz(name, emptyList())
        val bundle = quizBundle(entries)
        val stored = mutableMapOf<String, String?>()
        fun picture(side: String): String? {
            if (!side.startsWith(IMAGE_MARK)) return null
            val entry = side.removePrefix(IMAGE_MARK)
            val data = bundle.images[entry] ?: return null
            return stored.getOrPut(entry) { storeImage(store.imageDir, data) }
        }
        for (card in parseQzx(bundle.qzx)) {
            val imageA = picture(card.a)
            val imageB = picture(card.b)
            quiz = quiz.addCard(if (imageA != null) "" else card.a, if (imageB != null) "" else card.b, imageA, imageB)
        }
        return listOf(quiz)
    }

    private fun newQuiz(name: String, cards: List<QzxCard>) =
        cards.fold(Quiz(UUID.randomUUID().toString(), name)) { quiz, card -> quiz.addCard(card.a, card.b) }

    /** Plain .qzx when the quiz has no pictures; otherwise a zip with the pictures. */
    private fun writeQuiz(quiz: Quiz, out: OutputStream) {
        fun side(text: String, image: String?) = if (image != null) IMAGE_MARK + image else text
        val qzx = formatQzx(quiz.cards.map { QzxCard(side(it.a, it.imageA), side(it.b, it.imageB)) })
        val images = quiz.imageFiles()
        if (images.isEmpty()) {
            out.write(qzx.toByteArray(Charsets.UTF_8))
        } else {
            writeZip(out, quiz.name.replace(Regex("[/\\\\]"), "_"), qzx, images.associateWith { store.imageFile(it) })
        }
    }

    private fun displayName(resolver: ContentResolver, uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private fun persist(write: () -> Unit) {
        diskThread.execute {
            try {
                write()
            } catch (e: IOException) {
                message(text(R.string.save_failed))
            }
        }
    }

    private fun message(text: String) {
        _messages.trySend(text)
    }

    private fun text(@StringRes id: Int) = getApplication<Application>().getString(id)

    private fun plural(@PluralsRes id: Int, count: Int) =
        getApplication<Application>().resources.getQuantityString(id, count, count)
}

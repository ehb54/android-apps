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
import io.github.ehb54.quiz.data.SampleLibrary
import io.github.ehb54.quiz.data.storeImage
import io.github.ehb54.quiz.engine.AnswerTimes
import io.github.ehb54.quiz.engine.IMAGE_MARK
import io.github.ehb54.quiz.engine.MatchSession
import io.github.ehb54.quiz.engine.Outcome
import io.github.ehb54.quiz.engine.PairOutcome
import io.github.ehb54.quiz.engine.QzxCard
import io.github.ehb54.quiz.engine.SampleCatalog
import io.github.ehb54.quiz.engine.SampleEntry
import io.github.ehb54.quiz.engine.SampleFile
import io.github.ehb54.quiz.engine.SampleStatus
import io.github.ehb54.quiz.engine.Session
import io.github.ehb54.quiz.engine.Step
import io.github.ehb54.quiz.engine.buildCatalog
import io.github.ehb54.quiz.engine.cardsFromSheet
import io.github.ehb54.quiz.engine.formatQzx
import io.github.ehb54.quiz.engine.isXlsx
import io.github.ehb54.quiz.engine.isZip
import io.github.ehb54.quiz.engine.pairTiles
import io.github.ehb54.quiz.engine.parseQzx
import io.github.ehb54.quiz.engine.pickAnswer
import io.github.ehb54.quiz.engine.quizBundle
import io.github.ehb54.quiz.engine.readLimited
import io.github.ehb54.quiz.engine.readXlsx
import io.github.ehb54.quiz.engine.revealAnswer
import io.github.ehb54.quiz.engine.sampleEntries
import io.github.ehb54.quiz.engine.sampleStatus
import io.github.ehb54.quiz.engine.sha256
import io.github.ehb54.quiz.engine.startMatch
import io.github.ehb54.quiz.engine.startPickSession
import io.github.ehb54.quiz.engine.startSession
import io.github.ehb54.quiz.engine.submitAnswer
import io.github.ehb54.quiz.engine.unzip
import io.github.ehb54.quiz.engine.writeZip
import io.github.ehb54.quiz.model.Card
import io.github.ehb54.quiz.model.Quiz
import io.github.ehb54.quiz.model.QuizMode
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
    data object Matching : Screen
    data object Done : Screen
    data object Samples : Screen
}

enum class Feedback { Wrong, TooSlow }

/** Folder in assets with the built-in sample files. */
private const val SAMPLES = "samples"

/**
 * The sample library as the Samples screen shows it. [online]: the catalog
 * came from the download site just now (otherwise it is the last one seen,
 * or only the built-in samples). [busy]: sources being added or updated.
 */
data class LibraryState(
    val catalog: SampleCatalog? = null,
    val online: Boolean = false,
    val loading: Boolean = false,
    val busy: Set<String> = emptySet(),
)

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
    val match: MatchSession? = null,
    /** The last pair tried in match mode, when it was wrong: (side A card, side B card). */
    val mismatch: Pair<Long, Long>? = null,
    val summary: Summary? = null,
    val library: LibraryState = LibraryState(),
) {
    fun quiz(id: String?) = quizzes.find { it.id == id }

    fun quizFrom(source: String) = quizzes.find { it.source == source }
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
        val (loaded, session, match) = withContext(diskDispatcher) {
            val loaded = store.loadQuizzes()
            if (loaded.unreadable.isEmpty()) {
                store.removeUnusedImages(loaded.quizzes.flatMap { it.imageFiles() }.toSet())
            }
            Triple(loaded, store.loadSession(), store.loadMatch())
        }
        val active = session?.takeIf { s -> !s.finished && loaded.quizzes.any { it.id == s.quizId } }
        val activeMatch = match?.takeIf { m -> !m.finished && loaded.quizzes.any { it.id == m.quizId } }
        _state.value = UiState(
            quizzes = loaded.quizzes,
            session = active,
            match = activeMatch.takeIf { active == null },
            screen = when {
                active != null -> Screen.Asking
                activeMatch != null -> Screen.Matching
                else -> Screen.QuizList
            },
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

    fun deleteQuiz(id: String, showList: Boolean = true) {
        val quiz = _state.value.quiz(id) ?: return
        _state.update { s ->
            s.copy(quizzes = s.quizzes.filterNot { it.id == id }, screen = if (showList) Screen.QuizList else s.screen)
        }
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
        val session = when (quiz.settings.mode) {
            QuizMode.Typed -> startSession(quiz)
            QuizMode.Pick -> startPickSession(quiz)
            QuizMode.Match -> {
                startMatching(quiz)
                return
            }
        }
        if (session == null) {
            message(text(R.string.nothing_due))
            return
        }
        _state.update { it.copy(session = session, screen = Screen.Asking, feedback = null, revealed = false) }
        persist { store.saveSession(session) }
    }

    fun submit(typed: String, elapsedMillis: Long) = answer { quiz, session ->
        submitAnswer(quiz, session, typed, elapsedMillis)
    }

    /** Pick mode: the choice [picked] (a card) for the card on screen. */
    fun pick(picked: Long, elapsedMillis: Long) = answer { quiz, session ->
        pickAnswer(quiz, session, picked, elapsedMillis)
    }

    private fun answer(check: (Quiz, Session) -> Step) {
        val state = _state.value
        val session = state.session ?: return
        val quiz = state.quiz(session.quizId) ?: return
        val step = check(quiz, session)
        if (step.outcome == Outcome.Ignored) return
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
                confused = step.confused.map { card ->
                    card.answer(next.direction).ifBlank { text(R.string.picture) }
                },
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
                Outcome.Right, Outcome.Ignored -> null
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

    // Matching

    private fun startMatching(quiz: Quiz) {
        val match = startMatch(quiz)
        if (match == null) {
            message(text(R.string.nothing_due))
            return
        }
        _state.update { it.copy(match = match, mismatch = null, screen = Screen.Matching) }
        persist { store.saveMatch(match) }
    }

    /** Pairs the side A tile of card [left] with the side B tile of card [right]. */
    fun pair(left: Long, right: Long) {
        val state = _state.value
        val match = state.match ?: return
        val quiz = state.quiz(match.quizId) ?: return
        val step = pairTiles(quiz, match, left, right)
        if (step.outcome == PairOutcome.Ignored) return
        val quizzes = state.quizzes.map { if (it.id == quiz.id) step.quiz else it }
        val next = step.session
        if (next.finished) {
            val summary = Summary(
                quizId = quiz.id,
                total = next.total,
                firstTry = next.firstTry,
                misses = next.misses,
                rounds = next.round,
                times = AnswerTimes(),
                confused = emptyList(),
            )
            _state.value = state.copy(
                quizzes = quizzes,
                match = null,
                mismatch = null,
                summary = summary,
                screen = Screen.Done,
            )
            persist {
                store.saveQuiz(step.quiz)
                store.saveMatch(null)
            }
        } else {
            val mismatch = if (step.outcome == PairOutcome.Wrong) left to right else null
            _state.value = state.copy(quizzes = quizzes, match = next, mismatch = mismatch)
            persist {
                if (step.quiz !== quiz) store.saveQuiz(step.quiz)
                store.saveMatch(next)
            }
        }
    }

    fun clearMismatch() {
        if (_state.value.mismatch != null) _state.update { it.copy(mismatch = null) }
    }

    /**
     * Testing escape hatch (debug builds): ends the session, typed or match,
     * without finishing it. Results recorded so far are kept; no confusion
     * penalty is applied, and a match block in progress is not scored.
     */
    fun abandonSession() {
        val state = _state.value
        val quizId = state.session?.quizId ?: state.match?.quizId ?: return
        _state.update {
            it.copy(
                session = null,
                match = null,
                feedback = null,
                revealed = false,
                mismatch = null,
                screen = Screen.QuizDetail(quizId),
            )
        }
        persist {
            store.saveSession(null)
            store.saveMatch(null)
        }
    }

    /** There is no leaving a session: Back only says how much is left. */
    fun backDuringQuiz() {
        val remaining = _state.value.session?.remaining ?: _state.value.match?.remaining ?: return
        message(plural(R.plurals.finish_first, remaining))
    }

    fun closeSummary() = _state.update { s ->
        val id = s.summary?.quizId
        s.copy(summary = null, screen = if (s.quiz(id) != null) Screen.QuizDetail(id!!) else Screen.QuizList)
    }

    // Sample library

    private val library get() = SampleLibrary(getApplication<Application>().cacheDir)

    fun openSamples() {
        _state.update { it.copy(screen = Screen.Samples) }
        refreshLibrary()
    }

    /**
     * Fetches the catalog from the download site. Offline, shows the catalog as
     * last fetched, or, if there never was one, the samples built into the app.
     */
    fun refreshLibrary() {
        if (_state.value.library.loading) return
        _state.update { it.copy(library = it.library.copy(loading = true)) }
        viewModelScope.launch {
            val (catalog, online) = withContext(Dispatchers.IO) {
                runCatching { library.fetchCatalog() to true }.getOrElse {
                    (library.cachedCatalog() ?: runCatching { builtInCatalog() }.getOrNull()) to false
                }
            }
            _state.update { it.copy(library = it.library.copy(catalog = catalog, online = online, loading = false)) }
        }
    }

    fun addSample(source: String) = runSamples(listOf(source), update = false)

    fun updateSample(source: String) = runSamples(listOf(source), update = true)

    fun addAllSamples() {
        val entries = _state.value.library.catalog?.files.orEmpty().flatMap { it.quizzes }
        runSamples(entries.filter { _state.value.quizFrom(it.source) == null }.map { it.source }, update = false)
    }

    fun updateAllSamples() {
        val entries = _state.value.library.catalog?.files.orEmpty().flatMap { it.quizzes }
        val outdated = entries.filter { sampleStatus(it, _state.value.quizFrom(it.source)) == SampleStatus.UpdateAvailable }
        runSamples(outdated.map { it.source }, update = true)
    }

    /** Removes the quiz added from a sample; it can be added again (without its progress). */
    fun removeSample(source: String) {
        val quiz = _state.value.quizFrom(source) ?: return
        deleteQuiz(quiz.id, showList = false)
        message(text(R.string.sample_removed, quiz.name))
    }

    /** Adds, or with [update] replaces keeping progress, the sample quizzes [sources], one at a time. */
    private fun runSamples(sources: Collection<String>, update: Boolean) {
        val library = _state.value.library
        val wanted = library.catalog?.files.orEmpty()
            .flatMap { file -> file.quizzes.filter { it.source in sources && it.source !in library.busy }.map { file to it } }
        if (wanted.isEmpty()) {
            message(text(if (update) R.string.samples_up_to_date else R.string.samples_all_here))
            return
        }
        _state.update { it.copy(library = it.library.copy(busy = it.library.busy + wanted.map { (_, e) -> e.source })) }
        viewModelScope.launch {
            var done = 0
            for ((file, entry) in wanted) {
                val quiz = withContext(Dispatchers.IO) { runCatching { buildSample(file, entry) }.getOrNull() }
                val existing = _state.value.quizFrom(entry.source)
                when {
                    quiz == null -> message(text(R.string.sample_failed, entry.name))
                    existing == null -> {
                        addQuiz(quiz.copy(name = uniqueName(quiz.name)))
                        done++
                    }
                    update -> {
                        changeQuiz(existing.id) { it.replacedBy(quiz) }
                        done++
                    }
                }
                _state.update { it.copy(library = it.library.copy(busy = it.library.busy - entry.source)) }
            }
            when {
                done == 0 -> Unit
                wanted.size == 1 -> message(text(if (update) R.string.sample_updated_message else R.string.sample_added_message, wanted[0].second.name))
                else -> message(plural(if (update) R.plurals.samples_updated else R.plurals.samples_added, done))
            }
        }
    }

    /** One quiz from a sample file: the built-in copy if it matches the catalog, otherwise downloaded. */
    private fun buildSample(file: SampleFile, entry: SampleEntry): Quiz {
        val builtIn = if (file.builtIn) {
            runCatching { getApplication<Application>().assets.open("$SAMPLES/${file.file}").use { readLimited(it) } }
                .getOrNull()
        } else {
            null
        }
        val bytes = builtIn?.takeIf { sha256(it) == file.sha256 } ?: library.download(file)
        val actual = sampleEntries(file.file, bytes).first { it.source == entry.source }
        val quiz = if (entry.tab != null) {
            val sheet = readXlsx(unzip(bytes.inputStream())).first { it.name == entry.tab }
            newQuiz(sheet.name, cardsFromSheet(sheet))
        } else {
            readQuizzes(file.file, bytes).single()
        }
        return quiz.copy(source = actual.source, sourceVersion = actual.version)
    }

    /** A catalog of just the samples built into the app, for when the site was never reached. */
    private fun builtInCatalog(): SampleCatalog {
        val assets = getApplication<Application>().assets
        val files = assets.list(SAMPLES).orEmpty().associateWith { file ->
            assets.open("$SAMPLES/$file").use { readLimited(it) }
        }
        return buildCatalog(files, files.keys)
    }

    // Files

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

    private fun text(@StringRes id: Int, vararg args: Any) = getApplication<Application>().getString(id, *args)

    private fun plural(@PluralsRes id: Int, count: Int) =
        getApplication<Application>().resources.getQuantityString(id, count, count)
}

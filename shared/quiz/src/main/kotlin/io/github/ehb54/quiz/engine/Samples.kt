package io.github.ehb54.quiz.engine

import io.github.ehb54.quiz.model.MediaKind
import io.github.ehb54.quiz.model.Quiz
import io.github.ehb54.quiz.model.mediaKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * The sample library: catalog.json next to the sample files on the download
 * site, listing every file and the quizzes in it. Generated from the files by
 * the app's own code (SampleCatalogTest), so its fingerprints are exactly the
 * ones the app records when it adds a sample.
 */
@Serializable
data class SampleCatalog(val files: List<SampleFile>)

/** One sample file: its size and fingerprint, whether the app has it built in, and its quizzes. */
@Serializable
data class SampleFile(
    val file: String,
    val bytes: Long,
    val sha256: String,
    val builtIn: Boolean = false,
    val quizzes: List<SampleEntry>,
)

/**
 * One quiz in the library: a workbook [tab], or the whole file when [tab] is
 * null. [source] identifies it in added quizzes; [version] fingerprints its contents.
 */
@Serializable
data class SampleEntry(
    val name: String,
    val source: String,
    val version: String,
    val cards: Int,
    val pictures: Int = 0,
    val sounds: Int = 0,
    val tab: String? = null,
)

enum class SampleStatus { NotAdded, Added, UpdateAvailable }

fun sampleStatus(entry: SampleEntry, added: Quiz?): SampleStatus = when {
    added == null -> SampleStatus.NotAdded
    added.sourceVersion == entry.version -> SampleStatus.Added
    else -> SampleStatus.UpdateAvailable
}

fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** The quizzes in one sample file: one per non-empty workbook tab, otherwise one named after the file. */
fun sampleEntries(file: String, bytes: ByteArray): List<SampleEntry> {
    val name = file.substringBeforeLast('.').ifBlank { file }
    if (!isZip(bytes)) {
        return listOf(SampleEntry(name, file, sha256(bytes), parseQzx(bytes.toString(Charsets.UTF_8)).size))
    }
    val entries = unzip(bytes.inputStream())
    if (isXlsx(entries)) {
        return readXlsx(entries).mapNotNull { sheet ->
            val cards = cardsFromSheet(sheet).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            SampleEntry(sheet.name, "$file#${sheet.name}", sha256(formatQzx(cards).toByteArray()), cards.size, tab = sheet.name)
        }
    }
    val bundle = quizBundle(entries)
    val cards = parseQzx(bundle.qzx)
    // Cards with a picture, and cards with a sound (a side's, or one for after the answer), on either side.
    val sides = cards.map { card -> listOf(card.a, card.b).map { parseSide(it, bundle.files.keys) } }
    fun count(kind: MediaKind) = sides.count { both ->
        both.any { side -> listOfNotNull(side.file, side.sound).any { mediaKind(it) == kind } }
    }
    return listOf(SampleEntry(name, file, sha256(bytes), cards.size, count(MediaKind.Picture), count(MediaKind.Sound)))
}

/** A catalog of sample files (name to contents); [builtIn] names the ones shipped inside the app. */
fun buildCatalog(files: Map<String, ByteArray>, builtIn: Set<String>): SampleCatalog =
    SampleCatalog(
        files.toSortedMap().map { (file, bytes) ->
            SampleFile(file, bytes.size.toLong(), sha256(bytes), file in builtIn, sampleEntries(file, bytes))
        },
    )

private val catalogJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
}

fun SampleCatalog.toJson(): String = catalogJson.encodeToString(SampleCatalog.serializer(), this) + "\n"

fun parseCatalog(text: String): SampleCatalog = catalogJson.decodeFromString(SampleCatalog.serializer(), text)

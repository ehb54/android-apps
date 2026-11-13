package io.github.ehb54.quiz.engine

import io.github.ehb54.quiz.model.isSound
import io.github.ehb54.quiz.model.mediaKind
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Quiz files, in the original program's export format (.qzx): plain text,
 * one line for side A, then one line for side B, card after card.
 *
 * Cards with pictures or sounds travel as a .zip holding one .qzx file and
 * those files. A side written "!name.png" is the picture of that name and
 * "!name.mp3" the sound, by the file's extension (the original used a leading
 * "!" for sides that ran a program to show a molecule). What a side shows only
 * after the answer follows (see [parseSide]).
 */
data class QzxCard(val a: String, val b: String)

const val MEDIA_MARK = "!"

/** One side of a card in a zip: its text, its picture or sound ([file]), and a [sound] for after the answer. */
data class QzxSide(val text: String, val file: String? = null, val sound: String? = null)

/**
 * Reads one side of a card in a zip whose files are [files]. "!name" is the
 * file of that name; text after it is that file's text, shown after the answer
 * ("!hand.webp die Hand"). A side shown as text or a picture may end with a
 * sound for after the answer ("die Hand !hand.mp3", "!hand.webp die Hand
 * !hand.mp3"). Names are matched against the zip's files, so they may hold
 * spaces; a "!name" the zip lacks stays text.
 */
fun parseSide(side: String, files: Set<String>): QzxSide {
    var text = side
    var file: String? = null
    if (side.startsWith(MEDIA_MARK)) {
        val body = side.removePrefix(MEDIA_MARK)
        val ends = body.indices.filter { body[it] == ' ' } + body.length
        file = ends.map { body.substring(0, it) }.firstOrNull { it in files && mediaKind(it) != null }
        if (file != null) text = body.removePrefix(file).trim()
    }
    if (file != null && isSound(file)) return QzxSide(text, file)
    // A last " !name" that is a sound in the zip; " " first, so a side holding only "!name.mp3" after its file counts.
    val spaced = " $text"
    val at = spaced.indices.firstOrNull { i ->
        spaced.startsWith(" $MEDIA_MARK", i) && spaced.substring(i + 2).let { it in files && isSound(it) }
    } ?: return QzxSide(text, file)
    return QzxSide(spaced.substring(0, at).trim(), file, spaced.substring(at + 2))
}

/** One side as [parseSide] reads it. */
fun formatSide(side: QzxSide): String = listOfNotNull(
    side.file?.let { MEDIA_MARK + it },
    side.text.takeIf { it.isNotBlank() || side.file == null }?.let(::oneLine),
    side.sound?.takeIf { !isSound(side.file) }?.let { MEDIA_MARK + it },
).filter { it.isNotEmpty() }.joinToString(" ")

/** Upper bound on what a zip import may unpack, against zip bombs. */
const val MAX_IMPORT_BYTES = 256L * 1024 * 1024

fun parseQzx(text: String): List<QzxCard> {
    // A final newline ends the last line rather than starting an empty one; a
    // side A left without its side B is dropped, as the original import did.
    val lines = text.removePrefix("\uFEFF").removeSuffix("\n").split('\n').map { it.removeSuffix("\r") }
    return lines.chunked(2)
        .filter { it.size == 2 && (it[0].isNotBlank() || it[1].isNotBlank()) }
        .map { QzxCard(it[0], it[1]) }
}

fun formatQzx(cards: List<QzxCard>): String =
    cards.joinToString("") { "${oneLine(it.a)}\n${oneLine(it.b)}\n" }

private fun oneLine(text: String) = text.replace(Regex("[\r\n]+"), " ")

class QuizBundle(val qzx: String, val files: Map<String, ByteArray>)

fun isZip(bytes: ByteArray) =
    bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() &&
        bytes[2] == 3.toByte() && bytes[3] == 4.toByte()

/** Every file in a zip, by its path inside the zip, refusing more than [maxBytes] in total. */
fun unzip(input: InputStream, maxBytes: Long = MAX_IMPORT_BYTES): Map<String, ByteArray> {
    val entries = linkedMapOf<String, ByteArray>()
    var budget = maxBytes
    ZipInputStream(input).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (entry.isDirectory) continue
            val bytes = readLimited(zip, budget)
            budget -= bytes.size
            entries[entry.name] = bytes
        }
    }
    return entries
}

/** The first .qzx file and every picture and sound among unzipped [entries], keyed by file name without folders. */
fun quizBundle(entries: Map<String, ByteArray>): QuizBundle {
    var qzx: String? = null
    val files = mutableMapOf<String, ByteArray>()
    for ((path, bytes) in entries) {
        val name = path.substringAfterLast('/')
        if (name.startsWith(".")) continue
        val extension = name.substringAfterLast('.', "").lowercase()
        if (extension == "qzx" && qzx == null) {
            qzx = bytes.toString(Charsets.UTF_8)
        } else if (mediaKind(name) != null) {
            files[name] = bytes
        }
    }
    return QuizBundle(qzx ?: throw IOException("no .qzx file in the zip"), files)
}

fun readZip(input: InputStream, maxBytes: Long = MAX_IMPORT_BYTES) = quizBundle(unzip(input, maxBytes))

/** Reads all of [input], refusing more than [budget] bytes. */
fun readLimited(input: InputStream, budget: Long = MAX_IMPORT_BYTES): ByteArray {
    val buffer = java.io.ByteArrayOutputStream()
    val chunk = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val n = input.read(chunk)
        if (n < 0) break
        total += n
        if (total > budget) throw IOException("more than $MAX_IMPORT_BYTES bytes")
        buffer.write(chunk, 0, n)
    }
    return buffer.toByteArray()
}

/** Writes a zip with [name].qzx and the given pictures and sounds (zip entry name to file). */
fun writeZip(output: OutputStream, name: String, qzx: String, files: Map<String, File>) {
    ZipOutputStream(output).use { zip ->
        zip.putNextEntry(ZipEntry("$name.qzx"))
        zip.write(qzx.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        for ((entryName, file) in files) {
            zip.putNextEntry(ZipEntry(entryName))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }
}

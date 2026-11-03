package io.github.ehb54.quiz.engine

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
 * Cards with pictures travel as a .zip holding one .qzx file and the image
 * files; a side written "!name.png" is the picture of that name (the original
 * used a leading "!" for sides that ran a program to show a molecule).
 */
data class QzxCard(val a: String, val b: String)

const val IMAGE_MARK = "!"

private val imageExtensions = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")

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

class QuizBundle(val qzx: String, val images: Map<String, ByteArray>)

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

/** The first .qzx file and every image among unzipped [entries]; images are keyed by file name without folders. */
fun quizBundle(entries: Map<String, ByteArray>): QuizBundle {
    var qzx: String? = null
    val images = mutableMapOf<String, ByteArray>()
    for ((path, bytes) in entries) {
        val name = path.substringAfterLast('/')
        if (name.startsWith(".")) continue
        val extension = name.substringAfterLast('.', "").lowercase()
        if (extension == "qzx" && qzx == null) {
            qzx = bytes.toString(Charsets.UTF_8)
        } else if (extension in imageExtensions) {
            images[name] = bytes
        }
    }
    return QuizBundle(qzx ?: throw IOException("no .qzx file in the zip"), images)
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

/** Writes a zip with [name].qzx and the given images (zip entry name to file). */
fun writeZip(output: OutputStream, name: String, qzx: String, images: Map<String, File>) {
    ZipOutputStream(output).use { zip ->
        zip.putNextEntry(ZipEntry("$name.qzx"))
        zip.write(qzx.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        for ((entryName, file) in images) {
            zip.putNextEntry(ZipEntry(entryName))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }
}

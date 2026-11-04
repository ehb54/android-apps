package io.github.ehb54.quiz.data

import java.io.File
import java.util.UUID

/**
 * The kind of sound file [bytes] hold, as the extension to store it under,
 * read from the first bytes rather than a file name: "wav", "ogg", "flac",
 * "m4a", "mp3" or "aac". Null for anything else.
 */
fun soundExtension(bytes: ByteArray): String? {
    fun text(at: Int, expected: String) =
        bytes.size >= at + expected.length && expected.indices.all { bytes[at + it] == expected[it].code.toByte() }
    fun byte(at: Int) = if (bytes.size > at) bytes[at].toInt() and 0xff else -1
    return when {
        text(0, "RIFF") && text(8, "WAVE") -> "wav"
        text(0, "OggS") -> "ogg"
        text(0, "fLaC") -> "flac"
        text(4, "ftyp") -> "m4a"
        text(0, "ID3") -> "mp3"
        // A bare MPEG audio frame: 11 sync bits, then layer bits 01-11 (layer III to I).
        byte(0) == 0xff && (byte(1) and 0xe0) == 0xe0 && (byte(1) and 0x06) != 0 -> "mp3"
        // An AAC ADTS frame: 12 sync bits, then layer bits 00.
        byte(0) == 0xff && (byte(1) and 0xf6) == 0xf0 -> "aac"
        else -> null
    }
}

/**
 * Stores a sound in [dir] for use on a card, as it is (sounds are small).
 * Returns the new file name, or null when [bytes] are not a sound file.
 */
fun storeSound(dir: File, bytes: ByteArray): String? {
    val extension = soundExtension(bytes) ?: return null
    val name = "${UUID.randomUUID()}.$extension"
    dir.mkdirs()
    File(dir, name).writeBytes(bytes)
    return name
}

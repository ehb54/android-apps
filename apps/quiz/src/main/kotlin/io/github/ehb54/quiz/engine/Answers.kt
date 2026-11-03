package io.github.ehb54.quiz.engine

import java.text.Normalizer
import java.util.Locale

private val whitespace = Regex("\\s+")

/** What a keyboard without German letters or curly quotes types instead. */
private val keyboardEquivalents = mapOf(
    'ä' to "ae",
    'ö' to "oe",
    'ü' to "ue",
    'ß' to "ss",
    '’' to "'",
    '‘' to "'",
)

/**
 * Forgiving comparison form: letter case, extra spaces and how accents are
 * encoded do not count, ä/ö/ü/ß may be typed as ae/oe/ue/ss, and curly
 * apostrophes count as straight ones. Other accents still matter.
 */
fun normalizeAnswer(text: String): String {
    val plain = Normalizer.normalize(text, Normalizer.Form.NFC)
        .trim()
        .replace(whitespace, " ")
        .lowercase(Locale.ROOT)
    return buildString { plain.forEach { append(keyboardEquivalents[it] ?: it) } }
}

fun answersMatch(typed: String, expected: String) = normalizeAnswer(typed) == normalizeAnswer(expected)

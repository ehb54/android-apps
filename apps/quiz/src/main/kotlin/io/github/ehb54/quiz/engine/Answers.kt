package io.github.ehb54.quiz.engine

import java.text.Normalizer
import java.util.Locale

private val whitespace = Regex("\\s+")

/** Forgiving comparison form: letter case, accents' encoding and extra spaces do not count. */
fun normalizeAnswer(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFC)
        .trim()
        .replace(whitespace, " ")
        .lowercase(Locale.ROOT)

fun answersMatch(typed: String, expected: String) = normalizeAnswer(typed) == normalizeAnswer(expected)

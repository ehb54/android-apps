package io.github.ehb54.quiz.data

import io.github.ehb54.quiz.engine.MatchSession
import io.github.ehb54.quiz.engine.Session
import io.github.ehb54.quiz.model.Card
import io.github.ehb54.quiz.model.Direction
import io.github.ehb54.quiz.model.Quiz
import io.github.ehb54.quiz.model.QuizMode
import io.github.ehb54.quiz.model.isSound
import kotlinx.serialization.Serializable
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/*
 * Report issue: what is wrong with which cards, sent in the background to a
 * Google Form whose answers land in the maintainer's sheet; the user never
 * sees the form. A report that can't be sent waits in the store's outbox
 * (QuizStore.loadReports) and goes with the next one, or when the app starts.
 * See apps/quiz/DESIGN.md, Report issue.
 */

/**
 * Whether a report has a free-text note: for now, to learn what the list of
 * problems should hold. Off before the app goes public, so the form takes only
 * the list's answers and the app's own text.
 */
const val REPORT_NOTES = true

/** What a report says is wrong; [form] is what the form records, whatever the app's language. */
enum class ReportProblem(val form: String) {
    Picture("Picture wrong"),
    Sound("Sound or voice wrong"),
    Text("Word or spelling wrong"),
    Hint("English (hint) wrong"),
    Pair("These don't belong together"),
    Other("Something else"),
}

/** One report, as the form's questions take it (see [REPORT_FIELDS]). */
@Serializable
data class Report(
    val problem: String,
    val note: String,
    val quiz: String,
    val mode: String,
    val cards: String,
    val app: String,
    val device: String,
) {
    fun fields() = mapOf(
        "Problem" to problem,
        "Note" to note,
        "Quiz" to quiz,
        "Mode" to mode,
        "Cards" to cards,
        "App" to app,
        "Device" to device,
    )
}

/** The form's questions, by the names their answers carry in its pre-filled link. */
val REPORT_FIELDS = listOf("Problem", "Note", "Quiz", "Mode", "Cards", "App", "Device")

/**
 * The form's pre-filled link ("Get pre-filled link" in Google Forms, each
 * question answered with its own name from [REPORT_FIELDS]). It is public in
 * every build, so the form takes nothing but text into the sheet. Empty, reports
 * wait in the outbox.
 */
const val REPORT_FORM_LINK = "https://docs.google.com/forms/d/e/" +
    "1FAIpQLScfJdF2MjmruJslHtq4GM9e5pB6gifZEfGAm77kxdhmXjNAAA/viewform?usp=pp_url" +
    "&entry.2125151808=Problem&entry.675958693=Note&entry.213648362=Quiz" +
    "&entry.1205958006=Mode&entry.1588053151=Cards&entry.338846460=App" +
    "&entry.1814897463=Device"

/** Where reports go, and whether they have a note; tests change both. */
object ReportForm {
    var link: String = REPORT_FORM_LINK

    /**
     * [REPORT_NOTES]. UI tests turn it off: a text field in a dialog keeps
     * Robolectric's Compose from ever going idle (the card editor's would too).
     */
    var notes: Boolean = REPORT_NOTES
}

/** A form to post to: its formResponse address, and each question's entry name. */
data class FormTarget(val action: String, val entries: Map<String, String>)

/** The form behind a pre-filled link, or null when [link] is not one (or empty). */
fun parseFormLink(link: String): FormTarget? {
    val base = link.substringBefore('?')
    if (!base.endsWith("/viewform")) return null
    val entries = link.substringAfter('?', "").split('&').mapNotNull { pair ->
        val name = pair.substringBefore('=')
        val value = URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8")
        if (name.startsWith("entry.") && value in REPORT_FIELDS) value to name else null
    }.toMap()
    if (entries.isEmpty()) return null
    return FormTarget(base.removeSuffix("viewform") + "formResponse", entries)
}

/** The body of the form's POST: each answered question's entry with its value. */
fun formBody(target: FormTarget, report: Report): String {
    fun encode(text: String) = URLEncoder.encode(text, "UTF-8")
    return report.fields().mapNotNull { (question, value) ->
        target.entries[question]?.let { "${encode(it)}=${encode(value)}" }
    }.joinToString("&")
}

/** Posts [report] to the form; true when it was taken. Network work: not on the UI thread. */
fun sendReport(target: FormTarget, report: Report): Boolean {
    val connection = try {
        URI(target.action).toURL().openConnection() as HttpURLConnection
    } catch (e: Exception) {
        return false
    }
    return try {
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
        connection.outputStream.use { it.write(formBody(target, report).toByteArray(Charsets.UTF_8)) }
        connection.responseCode in 200..399
    } catch (e: IOException) {
        false
    } finally {
        connection.disconnect()
    }
}

// What a report says about the screen, in plain English for whoever reads the sheet.

/** The quiz: its name, and for a sample its file and version ("Core pictures ERNIE · Core pictures ERNIE.zip · 1a2b3c4d"). */
fun reportQuiz(quiz: Quiz): String =
    listOfNotNull(quiz.name, quiz.source, quiz.sourceVersion?.take(8)).joinToString(" · ")

private fun arrow(direction: Direction) = if (direction == Direction.AToB) "A → B" else "B → A"

/** "Type A → B, round 1", "Pick B → A, round 2", "Speed A → B, level 3, round 1". */
fun reportMode(session: Session): String {
    val kind = when {
        session.speed != null -> "Speed"
        session.picking -> "Pick"
        else -> "Type"
    }
    val level = session.speed?.let { ", level ${it.level}" }.orEmpty()
    return "$kind ${arrow(session.direction)}$level, round ${session.round}"
}

fun reportMode(match: MatchSession) = "Match, round ${match.round}"

/** After a right answer: "Pick A → B, after the answer". */
fun reportMode(mode: QuizMode, direction: Direction): String {
    val kind = when (mode) {
        QuizMode.Typed -> "Type"
        QuizMode.Pick -> "Pick"
        QuizMode.Match -> "Match"
        QuizMode.Speed -> "Speed"
    }
    return "$kind ${arrow(direction)}, after the answer"
}

/**
 * One card: its number in the quiz (a sample's cards are numbered in the
 * file's order) and both sides, files as [picture] and [sound], then its hint:
 * "#12 [picture] | die Hand [sound after] · hint: hand".
 */
fun describeCard(card: Card): String {
    fun side(text: String, media: String?, sound: String?) = listOfNotNull(
        media?.let { if (isSound(it)) "[sound]" else "[picture]" },
        text.takeIf { it.isNotBlank() },
        sound?.let { "[sound after]" },
    ).joinToString(" ")
    val hint = card.hint.takeIf { it.isNotBlank() }?.let { " · hint: $it" }.orEmpty()
    return "#${card.id} ${side(card.a, card.mediaA, card.soundA)} | ${side(card.b, card.mediaB, card.soundB)}$hint"
}

/** Type, Pick and Speed: the card asked, then each choice in screen order; [marked] is the sound choice played last. */
fun reportCards(quiz: Quiz, session: Session, marked: Long?): String {
    val lines = mutableListOf<String>()
    session.current?.let(quiz::card)?.let { lines += "asked: " + describeCard(it) }
    session.choices.forEachIndexed { index, id ->
        val card = quiz.card(id) ?: return@forEachIndexed
        val notes = listOfNotNull(
            "marked".takeIf { id == marked },
            "ruled out".takeIf { id in session.ruledOut },
            "right".takeIf { id == session.current },
        ).joinToString("") { " ($it)" }
        lines += "choice ${index + 1}$notes: " + describeCard(card)
    }
    return lines.joinToString("\n")
}

/**
 * Match: the picked tiles first, the last pair made, then the tiles still on
 * screen by column and place (left 1 is the top of side A).
 */
fun reportCards(quiz: Quiz, match: MatchSession, pickedLeft: Long?, pickedRight: Long?, lastPair: Pair<Long, Long>?): String {
    val lines = mutableListOf<String>()
    fun tile(column: String, tiles: List<Long>, id: Long) =
        "$column ${tiles.indexOf(id) + 1}: " + (quiz.card(id)?.let(::describeCard) ?: "#$id")
    pickedLeft?.let { lines += "picked " + tile("left", match.left, it) }
    pickedRight?.let { lines += "picked " + tile("right", match.right, it) }
    lastPair?.let { (left, right) ->
        lines += "last pair, left: " + (quiz.card(left)?.let(::describeCard) ?: "#$left")
        lines += "last pair, right: " + (quiz.card(right)?.let(::describeCard) ?: "#$right")
    }
    match.left.filter { it !in match.matchedLeft }.forEach { lines += tile("left", match.left, it) }
    match.right.filter { it !in match.matchedRight }.forEach { lines += tile("right", match.right, it) }
    return lines.joinToString("\n")
}

/** The card shown whole after a right answer. */
fun reportCards(card: Card) = "answered: " + describeCard(card)

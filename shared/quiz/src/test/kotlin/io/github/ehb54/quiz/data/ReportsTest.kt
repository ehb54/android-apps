package io.github.ehb54.quiz.data

import com.sun.net.httpserver.HttpServer
import io.github.ehb54.quiz.engine.startMatch
import io.github.ehb54.quiz.engine.startPickSession
import io.github.ehb54.quiz.model.Quiz
import io.github.ehb54.quiz.model.QuizMode
import io.github.ehb54.quiz.model.QuizSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URLDecoder
import kotlin.random.Random

class ReportsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val questions = REPORT_FIELDS.mapIndexed { index, name -> "entry.${100 + index}=$name" }.joinToString("&")

    private val report = Report(
        problem = ReportProblem.Sound.form,
        note = "too fast & too low",
        quiz = "Core dictation · Core dictation.zip · 1a2b3c4d",
        mode = "Pick A → B, round 1",
        cards = "asked: #1 [sound] | die Hand · hint: hand",
        app = "Quiz 0.1.63-abc1234 (63)",
        device = "Android 9 (API 28), samsung SM-G955F",
    )

    private fun decode(body: String) = body.split('&').associate { pair ->
        URLDecoder.decode(pair.substringBefore('='), "UTF-8") to URLDecoder.decode(pair.substringAfter('='), "UTF-8")
    }

    @Test
    fun aPrefilledLinkGivesTheFormAndItsQuestions() {
        val target = parseFormLink("https://docs.google.com/forms/d/e/1FAIpQ-x/viewform?usp=pp_url&$questions")!!
        assertEquals("https://docs.google.com/forms/d/e/1FAIpQ-x/formResponse", target.action)
        assertEquals("entry.100", target.entries["Problem"])
        assertEquals("entry.106", target.entries["Device"])
        assertNull(parseFormLink(""))
        assertNull(parseFormLink("https://docs.google.com/forms/d/e/1FAIpQ-x/viewform"))
        assertNull(parseFormLink("https://example.com/report?entry.1=Problem"))
    }

    @Test
    fun theAppsFormLinkHasEveryQuestion() {
        val target = parseFormLink(REPORT_FORM_LINK)!!
        assertTrue(target.action.endsWith("/formResponse"))
        assertEquals(REPORT_FIELDS.toSet(), target.entries.keys)
    }

    @Test
    fun theBodyAnswersTheQuestionsTheFormHas() {
        val target = FormTarget("https://x/formResponse", mapOf("Problem" to "entry.1", "Note" to "entry.2"))
        val fields = decode(formBody(target, report))
        assertEquals(mapOf("entry.1" to "Sound or voice wrong", "entry.2" to "too fast & too low"), fields)
    }

    @Test
    fun aReportIsPostedToTheForm() {
        var body = ""
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/forms/d/e/t/formResponse") { exchange ->
            body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()
        try {
            val port = server.address.port
            val target = parseFormLink("http://127.0.0.1:$port/forms/d/e/t/viewform?usp=pp_url&$questions")!!
            assertTrue(sendReport(target, report))
            val fields = decode(body)
            assertEquals(report.fields().values.toList(), (100..106).map { fields["entry.$it"] })
        } finally {
            server.stop(0)
        }
        // Nothing there: not sent, so it waits.
        val closed = ServerSocket(0).use { it.localPort }
        assertFalse(sendReport(parseFormLink("http://127.0.0.1:$closed/f/viewform?entry.1=Problem")!!, report))
    }

    @Test
    fun cardsAreDescribedForTheSheet() {
        val quiz = Quiz("q", "Dictation", settings = QuizSettings(mode = QuizMode.Pick, shuffle = false, choices = 3))
            .addCard("", "die Hand", mediaA = "1.mp3", hint = "hand")
            .addCard("", "der Fuß", mediaA = "2.mp3", hint = "foot")
            .addCard("Auge", "", mediaB = "eye.webp", soundA = "3.mp3")
        assertEquals("#1 [sound] | die Hand · hint: hand", describeCard(quiz.cards[0]))
        assertEquals("#3 Auge [sound after] | [picture]", describeCard(quiz.cards[2]))

        val session = startPickSession(quiz, Random(1))!!
        val lines = reportCards(quiz, session, marked = session.choices.first()).lines()
        assertEquals("asked: #1 [sound] | die Hand · hint: hand", lines.first())
        assertEquals(1 + session.choices.size, lines.size)
        assertTrue(lines[1].startsWith("choice 1 (marked)"))
        assertEquals(1, lines.count { "(right)" in it })
        assertEquals("Pick A → B, round 1", reportMode(session))
        assertEquals("Dictation", reportQuiz(quiz))
        assertEquals("Dictation · Core dictation.zip · 1a2b3c4d", reportQuiz(quiz.copy(source = "Core dictation.zip", sourceVersion = "1a2b3c4d5e6f")))

        val match = startMatch(quiz.copy(settings = quiz.settings.copy(mode = QuizMode.Match)), Random(1))!!
        val tiles = reportCards(quiz, match, pickedLeft = match.left[1], pickedRight = null, lastPair = null).lines()
        assertEquals("picked left 2: " + describeCard(quiz.card(match.left[1])!!), tiles.first())
        assertEquals(1 + match.left.size + match.right.size, tiles.size)
        assertEquals("Match, round 1", reportMode(match))
    }

    @Test
    fun reportsWaitInTheStore() {
        val store = QuizStore(folder.root)
        assertEquals(emptyList<Report>(), store.loadReports())
        store.saveReports(listOf(report, report.copy(note = "")))
        assertEquals(listOf(report, report.copy(note = "")), QuizStore(folder.root).loadReports())
        store.saveReports(emptyList())
        assertFalse(java.io.File(folder.root, "reports.json").exists())
    }
}

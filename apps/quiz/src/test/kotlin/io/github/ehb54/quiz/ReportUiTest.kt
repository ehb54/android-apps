package io.github.ehb54.quiz

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import com.sun.net.httpserver.HttpServer
import io.github.ehb54.quiz.data.REPORT_FIELDS
import io.github.ehb54.quiz.data.ReportForm
import io.github.ehb54.quiz.model.QuizMode
import io.github.ehb54.quiz.model.QuizSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.shadows.ShadowMediaPlayer
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Report an issue from a Pick session: the report waits on the phone while
 * the form can't be reached and goes by itself on the next start; one made
 * when it can goes at once. The quiz carries on as it was either way.
 */
class ReportUiTest : QuizUiTest() {
    init {
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(60_000, 0) }
        // A text field in a dialog keeps Robolectric's Compose from going idle; ReportsTest covers notes.
        ReportForm.notes = false
    }

    /** What the form got, report by report, by question. */
    private val received = LinkedBlockingQueue<Map<String, String>>()

    private fun report(problem: String) {
        compose.onNodeWithContentDescription("Report an issue").performClick()
        compose.onNodeWithText(problem).performClick()
        compose.onNodeWithText("Send").performClick()
    }

    @Test
    fun aReportGoesToTheFormOrWaitsUntilItCan() {
        val port = ServerSocket(0).use { it.localPort }
        val questions = REPORT_FIELDS.joinToString("&") { "entry.$it=$it" }
        ReportForm.link = "http://127.0.0.1:$port/forms/d/e/test/viewform?usp=pp_url&$questions"
        saveDictationQuiz(QuizSettings(mode = QuizMode.Pick, shuffle = false, choices = 3))

        ActivityScenario.launch(MainActivity::class.java).use {
            startQuiz("Dictation", 3)
            compose.onNodeWithContentDescription("Stop sound").performClick()
            report("Sound or voice wrong")
            waitForMessage("Report saved. It goes out once the app can reach the internet.")
            compose.onNodeWithText("die Hand").performClick()
            compose.waitForText("Round 1 · 2 left")
        }

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
        server.createContext("/forms/d/e/test/formResponse") { exchange ->
            val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            received.add(
                body.split('&').associate {
                    URLDecoder.decode(it.substringBefore('='), "UTF-8").removePrefix("entry.") to
                        URLDecoder.decode(it.substringAfter('='), "UTF-8")
                },
            )
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                // The waiting report goes by itself, with what was on screen.
                val waited = received.poll(20, TimeUnit.SECONDS)
                assertNotNull("the waiting report never came", waited)
                waited!!
                assertEquals("Sound or voice wrong", waited["Problem"])
                assertEquals("", waited["Note"])
                assertEquals("Dictation", waited["Quiz"])
                assertEquals("Pick A → B, round 1", waited["Mode"])
                assertTrue(waited["Cards"]!!, waited["Cards"]!!.startsWith("asked: #1 [sound] | die Hand · hint: hand\nchoice 1"))
                assertTrue(waited["App"]!!, waited["App"]!!.startsWith("Quiz "))
                assertTrue(waited["Device"]!!, waited["Device"]!!.startsWith("Android "))

                // A new one goes at once.
                compose.waitForText("Round 1 · 2 left")
                compose.onNodeWithContentDescription("Stop sound").performClick()
                report("Picture wrong")
                waitForMessage("Report sent. Thank you.")
                val sent = received.poll(20, TimeUnit.SECONDS)
                assertNotNull(sent)
                assertEquals("Picture wrong", sent!!["Problem"])
                assertEquals("", sent["Note"])
                assertTrue(sent["Cards"]!!, sent["Cards"]!!.startsWith("asked: #2 [sound] | der Fuß"))
            }
        } finally {
            server.stop(0)
        }
    }
}

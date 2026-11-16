package io.github.ehb54.quiz

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Looper
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.sun.net.httpserver.HttpServer
import io.github.ehb54.quiz.data.REPORT_FIELDS
import io.github.ehb54.quiz.data.ReportForm
import io.github.ehb54.quiz.model.QuizMode
import io.github.ehb54.quiz.model.QuizSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue

/**
 * A report that waits on the phone goes by itself while the app runs: as soon
 * as the network reaches the internet, or a minute later (a network that is
 * up, but can't reach the form).
 */
class ReportRetryUiTest : QuizUiTest() {
    init {
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(60_000, 0) }
        ReportForm.notes = false
    }

    /** What the form got, report by report, by question. */
    private val received = LinkedBlockingQueue<Map<String, String>>()

    /** The form takes reports; otherwise it answers 503, as a network with no way out would fail. */
    @Volatile
    private var up = false

    /** Tells the app, as Android would, that the network reaches the internet. */
    private fun networkIsBack() {
        val connectivity = ApplicationProvider.getApplicationContext<Context>().getSystemService(ConnectivityManager::class.java)
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        for (callback in shadowOf(connectivity).networkCallbacks) {
            callback.onCapabilitiesChanged(ShadowNetwork.newInstance(1), capabilities)
        }
    }

    @Test
    fun aWaitingReportGoesWhenTheNetworkIsBackOrAMinuteLater() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/forms/d/e/test/formResponse") { exchange ->
            val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            if (up) {
                received.add(
                    body.split('&').associate {
                        URLDecoder.decode(it.substringBefore('='), "UTF-8").removePrefix("entry.") to
                            URLDecoder.decode(it.substringAfter('='), "UTF-8")
                    },
                )
            }
            exchange.sendResponseHeaders(if (up) 200 else 503, -1)
            exchange.close()
        }
        server.start()
        val questions = REPORT_FIELDS.joinToString("&") { "entry.$it=$it" }
        ReportForm.link = "http://127.0.0.1:${server.address.port}/forms/d/e/test/viewform?usp=pp_url&$questions"
        saveDictationQuiz(QuizSettings(mode = QuizMode.Pick, shuffle = false, choices = 3))
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                startQuiz("Dictation", 3)
                compose.onNodeWithContentDescription("Stop sound").performClick()
                reportIssue("Sound or voice wrong")
                waitForMessage("Report saved. It goes out once the app can reach the internet.")

                up = true
                networkIsBack()
                assertEquals("Sound or voice wrong", compose.nextReport(received, "the report, when the network was back")["Problem"])

                up = false
                reportIssue("Picture wrong")
                waitForMessage("Report saved. It goes out once the app can reach the internet.")
                up = true
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(61))
                assertEquals("Picture wrong", compose.nextReport(received, "the report, a minute later")["Problem"])
            }
        } finally {
            server.stop(0)
        }
    }
}

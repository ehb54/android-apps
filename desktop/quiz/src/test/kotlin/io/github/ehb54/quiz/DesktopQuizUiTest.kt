package io.github.ehb54.quiz

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import io.github.ehb54.core.desktop.AppTheme
import io.github.ehb54.core.desktop.Application
import io.github.ehb54.core.desktop.BuildInfo
import io.github.ehb54.quiz.data.SampleLibrary
import io.github.ehb54.quiz.ui.QuizApp
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The Android app's screens and view model running on the desktop: the
 * stand-ins for Android's resources, assets, view model and plurals, through
 * adding a built-in sample and starting it.
 */
class DesktopQuizUiTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val folder = TemporaryFolder()

    private fun waitForText(text: String) = compose.waitUntil(15_000) {
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun addsABuiltInSampleAndStartsIt() {
        Application("Quiz", folder.root)
        SampleLibrary.baseUrl = System.getProperty("quiz.samples")
        compose.setContent {
            AppTheme {
                QuizApp(BuildInfo("test", 1, "now"))
            }
        }

        // The desktop's own wording replaces the Android app's.
        waitForText("No quizzes yet. Click + to make one.")

        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Sample quizzes…").performClick()
        waitForText("Up to date with the download site.")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("Add Chemical symbols"))
        compose.onNodeWithContentDescription("Add Chemical symbols").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Added Chemical symbols.")

        compose.onNodeWithContentDescription("Back").performClick()
        waitForText("14 cards · Type →14 ←14 · Pick →14 ←14 · Match 14")
        compose.onNodeWithText("Chemical symbols").performClick()
        waitForText("Start (14 cards due)")
        compose.onNodeWithText("Start (14 cards due)").performClick()
        waitForText("Round 1 · 14 left")
    }
}

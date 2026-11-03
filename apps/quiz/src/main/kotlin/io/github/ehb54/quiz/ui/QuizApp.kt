package io.github.ehb54.quiz.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.ehb54.core.ui.BuildInfo

/** Shows the screen the view model is on; a running session always comes back to it. */
@Composable
fun QuizApp(info: BuildInfo, vm: QuizViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    when (val screen = state.screen) {
        Screen.Loading -> Unit
        Screen.QuizList -> QuizListScreen(
            quizzes = state.quizzes,
            snackbar = snackbar,
            info = info,
            onOpen = vm::openQuiz,
            onCreate = vm::createQuiz,
            onImport = vm::importQuiz,
            onSamples = vm::openSamples,
        )
        is Screen.QuizDetail -> {
            val quiz = state.quiz(screen.quizId)
            if (quiz != null) {
                QuizDetailScreen(quiz, vm, snackbar)
            } else {
                LaunchedEffect(screen) { vm.showList() }
            }
        }
        Screen.Asking -> {
            val session = state.session
            val quiz = state.quiz(session?.quizId)
            if (session != null && quiz != null) {
                AskingScreen(quiz, session, state.feedback, state.revealed, vm.imageDir, snackbar, vm)
            }
        }
        Screen.Matching -> {
            val match = state.match
            val quiz = state.quiz(match?.quizId)
            if (match != null && quiz != null) {
                MatchScreen(quiz, match, state.mismatch, vm.imageDir, snackbar, vm)
            }
        }
        Screen.Samples -> SamplesScreen(state, vm, snackbar)
        Screen.Done -> state.summary?.let { summary ->
            DoneScreen(summary, state.quiz(summary.quizId)?.name.orEmpty(), vm::closeSummary)
        }
    }
}

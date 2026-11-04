package io.github.ehb54.quiz.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ehb54.quiz.R
import io.github.ehb54.quiz.engine.Session
import io.github.ehb54.quiz.model.Quiz
import java.io.File

/**
 * One card at a time: the prompt (text, picture or sound) and a field for the answer.
 * Wrong answers clear the field and the card stays; "Show answer" counts as a
 * miss and hides the answer again before you type it. Back does not leave.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskingScreen(
    quiz: Quiz,
    session: Session,
    feedback: Feedback?,
    revealed: Boolean,
    mediaDir: File,
    snackbar: SnackbarHostState,
    vm: QuizViewModel,
) {
    BackHandler(onBack = vm::backDuringQuiz)
    val card = session.current?.let(quiz::card) ?: return
    val direction = session.direction
    val attempt = listOf(session.round, card.id, session.missesOnCurrent)
    var typed by rememberSaveable(attempt) { mutableStateOf("") }
    // The timer starts when the card shows, or when its sound has played.
    var shownAt by remember(attempt, revealed) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val focus = remember { FocusRequester() }
    fun submit() {
        if (typed.isNotBlank()) vm.submit(typed, SystemClock.elapsedRealtime() - shownAt)
    }
    LaunchedEffect(attempt, revealed) {
        if (!revealed) focus.requestFocus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(quiz.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = { RoundCounter(session.round, session.remaining, vm::abandonSession) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { insets ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(insets)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Prompt(card, direction, mediaDir, pictureHeight = 360.dp, playKey = listOf(session.round, card.id)) {
                shownAt = SystemClock.elapsedRealtime()
            }

            if (revealed) {
                Text(stringResource(R.string.answer_is), style = MaterialTheme.typography.labelLarge)
                Text(card.answer(direction), style = MaterialTheme.typography.headlineSmall)
                Button(onClick = vm::hideAnswer, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.hide_answer))
                }
            } else {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text(stringResource(R.string.answer)) },
                    singleLine = true,
                    isError = feedback != null,
                    supportingText = feedback?.let {
                        { Text(stringResource(if (it == Feedback.Wrong) R.string.wrong else R.string.too_slow)) }
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = ::submit, enabled = typed.isNotBlank()) { Text(stringResource(R.string.check)) }
                    TextButton(onClick = vm::reveal) { Text(stringResource(R.string.show_answer)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoneScreen(summary: Summary, quizName: String, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Scaffold(topBar = { TopAppBar(title = { Text(quizName, maxLines = 1, overflow = TextOverflow.Ellipsis) }) }) {
            insets ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(insets)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.done_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.summary_first_try, summary.firstTry, summary.total))
            Text(pluralStringResource(R.plurals.summary_misses, summary.misses, summary.misses))
            Text(pluralStringResource(R.plurals.summary_rounds, summary.rounds, summary.rounds))
            val times = summary.times
            if (times.count > 0) {
                Text(
                    stringResource(
                        R.string.summary_times,
                        times.minMillis / 1000.0,
                        times.averageMillis / 1000.0,
                        times.maxMillis / 1000.0,
                    ),
                )
            }
            if (summary.confused.isNotEmpty()) {
                Text(stringResource(R.string.summary_confused))
                summary.confused.forEach { Text("• $it") }
            }
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ok)) }
        }
    }
}

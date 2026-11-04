package io.github.ehb54.quiz.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ehb54.quiz.R
import io.github.ehb54.quiz.engine.Session
import io.github.ehb54.quiz.model.Quiz
import java.io.File

/**
 * Pick mode: one card's prompt (text or picture) and a few choices from the
 * other side. A wrong choice is marked and can't be picked again; the card
 * stays until the right one is picked. Back does not leave.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickScreen(
    quiz: Quiz,
    session: Session,
    feedback: Feedback?,
    imageDir: File,
    snackbar: SnackbarHostState,
    vm: QuizViewModel,
) {
    BackHandler(onBack = vm::backDuringQuiz)
    val card = session.current?.let(quiz::card) ?: return
    val direction = session.direction
    val shownAt = remember(session.round, card.id, session.missesOnCurrent) { SystemClock.elapsedRealtime() }
    val colors = MaterialTheme.colorScheme

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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            card.promptImage(direction)?.let {
                CardImage(
                    File(imageDir, it),
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                )
            }
            val prompt = card.prompt(direction)
            if (prompt.isNotBlank()) Text(prompt, style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(
                    when (feedback) {
                        Feedback.Wrong -> R.string.not_that_one
                        Feedback.TooSlow -> R.string.too_slow
                        null -> R.string.pick_hint
                    },
                ),
                color = if (feedback != null) colors.error else colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            session.choices.forEach { id ->
                val choice = quiz.card(id) ?: return@forEach
                Choice(
                    text = choice.answer(direction),
                    image = choice.answerImage(direction),
                    imageDir = imageDir,
                    ruledOut = id in session.ruledOut,
                ) { vm.pick(id, SystemClock.elapsedRealtime() - shownAt) }
            }
        }
    }
}

@Composable
private fun Choice(text: String, image: String?, imageDir: File, ruledOut: Boolean, onPick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (image != null) 120.dp else 64.dp)
            .alpha(if (ruledOut) 0.6f else 1f)
            .clickable(enabled = !ruledOut, role = Role.Button, onClick = onPick),
        shape = MaterialTheme.shapes.medium,
        color = if (ruledOut) colors.errorContainer else colors.surfaceVariant,
        border = BorderStroke(1.dp, if (ruledOut) colors.error else colors.outlineVariant),
    ) {
        Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
            if (image != null) {
                CardImage(File(imageDir, image), Modifier.fillMaxSize(), maxSide = 512)
            } else {
                Text(
                    text,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

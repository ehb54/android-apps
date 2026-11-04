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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ehb54.quiz.R
import io.github.ehb54.quiz.engine.Session
import io.github.ehb54.quiz.model.Quiz
import io.github.ehb54.quiz.model.isSound
import java.io.File

/**
 * Pick mode: one card's prompt (text, picture or sound) and a few choices from
 * the other side. A wrong choice is marked and can't be picked again; the card
 * stays until the right one is picked. A sound choice has a button to play it;
 * a tap elsewhere on it picks it. Back does not leave.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickScreen(
    quiz: Quiz,
    session: Session,
    feedback: Feedback?,
    mediaDir: File,
    snackbar: SnackbarHostState,
    vm: QuizViewModel,
) {
    BackHandler(onBack = vm::backDuringQuiz)
    val card = session.current?.let(quiz::card) ?: return
    val direction = session.direction
    // The timer starts when the card shows, or when its sound has played.
    var shownAt by remember(session.round, card.id, session.missesOnCurrent) {
        mutableLongStateOf(SystemClock.elapsedRealtime())
    }
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
            Prompt(card, direction, mediaDir, pictureHeight = 280.dp, playKey = listOf(session.round, card.id)) {
                shownAt = SystemClock.elapsedRealtime()
            }
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
            session.choices.forEachIndexed { index, id ->
                val choice = quiz.card(id) ?: return@forEachIndexed
                Choice(
                    text = choice.answer(direction),
                    media = choice.answerMedia(direction),
                    mediaDir = mediaDir,
                    number = index + 1,
                    ruledOut = id in session.ruledOut,
                    modifier = Modifier.testTag(choiceTag(id)),
                ) { vm.pick(id, SystemClock.elapsedRealtime() - shownAt) }
            }
        }
    }
}

@Composable
private fun Choice(
    text: String,
    media: String?,
    mediaDir: File,
    number: Int,
    ruledOut: Boolean,
    modifier: Modifier = Modifier,
    onPick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val picture = media != null && !isSound(media)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(if (picture) 120.dp else 64.dp)
            .alpha(if (ruledOut) 0.6f else 1f)
            .clickable(enabled = !ruledOut, role = Role.Button, onClick = onPick),
        shape = MaterialTheme.shapes.medium,
        color = if (ruledOut) colors.errorContainer else colors.surfaceVariant,
        border = BorderStroke(1.dp, if (ruledOut) colors.error else colors.outlineVariant),
    ) {
        Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
            if (media != null && isSound(media)) {
                SoundTileContent(File(mediaDir, media), number)
            } else if (media != null) {
                CardImage(File(mediaDir, media), Modifier.fillMaxSize(), maxSide = 512)
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

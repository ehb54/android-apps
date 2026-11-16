package io.github.ehb54.quiz.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ehb54.quiz.R
import io.github.ehb54.quiz.data.ReportProblem
import io.github.ehb54.quiz.model.Card
import io.github.ehb54.quiz.model.Direction
import io.github.ehb54.quiz.model.isSound
import java.io.File

/**
 * After a card is answered right in Type or Pick: the whole card, prompt side
 * first, with what its sides carry for after the answer, a file's text (the word
 * a sound says) and the word spoken. With [playSound] (the quiz's "Play the
 * sound after the answer"), its sound plays once by itself, not again when the
 * phone is turned; its buttons play it either way. Next (or Back, or Enter on a
 * keyboard) goes on to the next card, whose clock starts then. A card with a
 * hint has Peek, which shows it here at no cost: the card is answered already.
 * The flag in the top bar reports an issue with the card ([onReport]);
 * [hints]: the quiz has some.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AfterAnswerScreen(
    quizName: String,
    card: Card,
    direction: Direction,
    playSound: Boolean,
    mediaDir: File,
    hints: Boolean,
    onReport: (ReportProblem, String) -> Unit,
    onNext: () -> Unit,
) {
    BackHandler(onBack = onNext)
    var reporting by rememberSaveable { mutableStateOf(false) }
    if (reporting) {
        ReportDialog(R.string.report_about_card, hints, { reporting = false }) { problem, note ->
            reporting = false
            onReport(problem, note)
        }
    }
    if (playSound) card.afterSound(direction)?.let { PlayOnce("${card.id}:$direction", File(mediaDir, it)) }
    val focus = remember { FocusRequester() }
    var peeked by rememberSaveable(card.id, direction) { mutableStateOf(false) }
    LaunchedEffect(reporting, peeked) { if (!reporting) focus.requestFocus() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(quizName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = { ReportButton { reporting = true } },
            )
        },
    ) { insets ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(insets)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.right_answer),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            val promptIsA = direction == Direction.AToB
            WholeSide(card, sideA = promptIsA, mediaDir)
            HorizontalDivider()
            WholeSide(card, sideA = !promptIsA, mediaDir)
            Peek(card.hint, peeked, { peeked = true })
            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            ) { Text(stringResource(R.string.next_card)) }
        }
    }
}

/** One side with everything it holds: its picture, its sound's button and its text. */
@Composable
internal fun WholeSide(card: Card, sideA: Boolean, mediaDir: File) {
    val media = if (sideA) card.mediaA else card.mediaB
    val text = if (sideA) card.a else card.b
    val sound = card.sound(sideA)
    if (media != null && !isSound(media)) {
        CardImage(
            File(mediaDir, media),
            Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp),
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (sound != null) SoundButton(File(mediaDir, sound))
        if (text.isNotBlank()) Text(text, style = MaterialTheme.typography.headlineMedium)
    }
}

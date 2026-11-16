package io.github.ehb54.quiz.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.ehb54.core.ui.R as CoreR
import io.github.ehb54.quiz.R
import io.github.ehb54.quiz.data.ReportProblem
import io.github.ehb54.quiz.model.Direction
import io.github.ehb54.quiz.model.Quiz
import java.io.File

/** The top bar's Look back button, in Type, Pick, Speed and Match, once a card has gone by. */
@Composable
fun LookBackButton(seen: List<Seen>, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = seen.isNotEmpty()) {
        Icon(painterResource(R.drawable.ic_look_back), contentDescription = stringResource(R.string.look_back))
    }
}

/**
 * Looking back, over the quiz screen, which waits as it was (its clock
 * stopped): the cards gone by ([seen], oldest first), the last one first, each
 * whole as after an answer. Earlier and Later step through them; Peek shows a
 * hint, and the sound buttons play. Nothing counts, either way. The flag
 * reports an issue with the card shown ([onReport]). Back to the quiz, or Back,
 * closes it ([onClose]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LookBack(
    quiz: Quiz,
    seen: List<Seen>,
    mediaDir: File,
    onReport: (Seen, ReportProblem, String) -> Unit,
    onClose: () -> Unit,
) {
    // How far back: 1 is the last card gone by.
    var back by rememberSaveable { mutableIntStateOf(1) }
    val shown = seen.getOrNull(seen.size - back.coerceIn(1, seen.size.coerceAtLeast(1)))
    val card = shown?.let { quiz.card(it.cardId) }
    if (shown == null || card == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val player = LocalSoundPlayer.current
    LaunchedEffect(Unit) { player.stop() }
    var reporting by rememberSaveable { mutableStateOf(false) }
    var peeked by rememberSaveable(back) { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler(onBack = onClose)
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(painterResource(CoreR.drawable.core_ui_ic_back), stringResource(R.string.look_back_return))
                        }
                    },
                    title = { Text(pluralStringResource(R.plurals.look_back_position, back, back)) },
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
                val promptIsA = shown.direction == Direction.AToB
                WholeSide(card, sideA = promptIsA, mediaDir)
                HorizontalDivider()
                WholeSide(card, sideA = !promptIsA, mediaDir)
                Peek(card.hint, peeked, { peeked = true })
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = { back++ }, enabled = back < seen.size, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.look_back_earlier))
                    }
                    OutlinedButton(onClick = { back-- }, enabled = back > 1, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.look_back_later))
                    }
                }
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.look_back_return))
                }
            }
        }
    }
    if (reporting) {
        ReportDialog(R.string.report_about_card, quiz.cards.any { it.hint.isNotBlank() }, { reporting = false }) { problem, note ->
            reporting = false
            onReport(shown, problem, note)
        }
    }
}

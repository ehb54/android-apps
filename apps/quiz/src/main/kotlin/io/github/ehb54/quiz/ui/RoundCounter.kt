package io.github.ehb54.quiz.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ehb54.quiz.BuildConfig
import io.github.ehb54.quiz.R

/**
 * Testing only: long-pressing the round counter offers to abandon the session.
 * Debug builds only (release builds, as published, never have it); set to
 * false to turn it off sooner.
 */
private val ESCAPE_HATCH = BuildConfig.DEBUG

/** "Round 2 · 5 left" in a running session's top bar (typed or match). */
@Composable
fun RoundCounter(round: Int, remaining: Int, onLeave: () -> Unit) {
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.leave_title)) },
            text = { Text(stringResource(R.string.leave_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    onLeave()
                }) { Text(stringResource(R.string.leave)) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    val escape = if (ESCAPE_HATCH) {
        Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { confirmLeave = true }) }
    } else {
        Modifier
    }
    Text(
        stringResource(
            R.string.two_parts,
            stringResource(R.string.round, round),
            pluralStringResource(R.plurals.cards_left, remaining, remaining),
        ),
        escape.padding(end = 16.dp),
        style = MaterialTheme.typography.labelLarge,
    )
}

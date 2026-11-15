package io.github.ehb54.quiz.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.ehb54.quiz.R
import io.github.ehb54.quiz.data.ReportForm
import io.github.ehb54.quiz.data.ReportProblem

/** The top bar's Report an issue button, in Type, Pick, Speed, Match and after an answer. */
@Composable
fun ReportButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(painterResource(R.drawable.ic_report), contentDescription = stringResource(R.string.report_issue))
    }
}

/**
 * Report an issue: what is wrong, from a short list, and (while [ReportForm.notes])
 * a note. [about] says what goes with it. Send hands both to [onSend], which
 * sends the report in the background; the quiz carries on as it was.
 */
@Composable
fun ReportDialog(@StringRes about: Int, hints: Boolean, onDismiss: () -> Unit, onSend: (ReportProblem, String) -> Unit) {
    var problem by rememberSaveable { mutableStateOf<ReportProblem?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    val problems = ReportProblem.entries.filter { hints || it != ReportProblem.Hint }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.report_issue)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(about), style = MaterialTheme.typography.bodyMedium)
                for (each in problems) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = each == problem, role = Role.RadioButton) { problem = each }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = each == problem, onClick = null)
                        Text(stringResource(each.label()), Modifier.padding(start = 8.dp))
                    }
                }
                if (ReportForm.notes) {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text(stringResource(R.string.report_note)) },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { problem?.let { onSend(it, note) } }, enabled = problem != null) {
                Text(stringResource(R.string.report_send))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun ReportProblem.label() = when (this) {
    ReportProblem.Picture -> R.string.report_picture
    ReportProblem.Sound -> R.string.report_sound
    ReportProblem.Text -> R.string.report_text
    ReportProblem.Hint -> R.string.report_hint
    ReportProblem.Pair -> R.string.report_pair
    ReportProblem.Other -> R.string.report_other
}

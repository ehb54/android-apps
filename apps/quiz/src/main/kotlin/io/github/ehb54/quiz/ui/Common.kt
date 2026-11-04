package io.github.ehb54.quiz.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.ehb54.quiz.R
import io.github.ehb54.quiz.data.loadImage
import io.github.ehb54.quiz.model.Progress
import io.github.ehb54.quiz.model.QuizMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import io.github.ehb54.core.ui.R as CoreR

/** The ⋮ button in a top bar, with its menu of (label, action) entries. */
@Composable
fun OverflowMenu(entries: List<Pair<Int, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(CoreR.drawable.core_ui_ic_more), stringResource(R.string.more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            entries.forEach { (label, action) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        open = false
                        action()
                    },
                )
            }
        }
    }
}

@Composable
fun NameDialog(
    @StringRes title: Int,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.quiz_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun ConfirmDialog(
    text: String,
    @StringRes confirm: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** A label with − and + buttons stepping through [values]. */
@Composable
fun Stepper(
    label: String,
    value: Int,
    values: List<Int>,
    valueText: String,
    onChange: (Int) -> Unit,
) {
    val index = values.indexOf(value).let { if (it < 0) values.indexOfLast { v -> v < value } else it }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        TextButton(onClick = { onChange(values[index - 1]) }, enabled = index > 0) { Text("−") }
        Text(valueText, Modifier.widthIn(min = 48.dp), textAlign = TextAlign.Center)
        TextButton(onClick = { onChange(values[index + 1]) }, enabled = index < values.lastIndex) { Text("+") }
    }
}

/** A card picture, decoded off the main thread at no more than [maxSide] pixels. */
@Composable
fun CardImage(file: File, modifier: Modifier = Modifier, maxSide: Int = 1600) {
    val bitmap by produceState<ImageBitmap?>(null, file, maxSide) {
        value = withContext(Dispatchers.IO) { loadImage(file, maxSide)?.asImageBitmap() }
    }
    val loaded = bitmap
    if (loaded != null) {
        Image(loaded, stringResource(R.string.picture), modifier, contentScale = ContentScale.Fit)
    } else {
        Box(modifier)
    }
}

/** "Type", "Pick" or "Match". */
@StringRes
fun modeLabel(mode: QuizMode) = when (mode) {
    QuizMode.Typed -> R.string.mode_type
    QuizMode.Pick -> R.string.mode_pick
    QuizMode.Match -> R.string.mode_match
}

/**
 * How many cards are due: "2", or when none are, a check with the level every card
 * has reached ("✓2"). Null when no card can be asked this way.
 */
@Composable
fun dueText(progress: Progress): String? = when {
    progress.lowest == null -> null
    progress.due == 0 -> stringResource(R.string.cleared_to, progress.lowest)
    else -> progress.due.toString()
}

/** A label with its due count: "Pick 2", "Pick ✓2", or just "Pick" when no card can be asked that way. */
@Composable
fun dueLabel(label: String, progress: Progress): String =
    dueText(progress)?.let { stringResource(R.string.label_due, label, it) } ?: label

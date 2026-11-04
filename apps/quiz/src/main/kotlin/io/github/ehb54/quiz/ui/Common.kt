package io.github.ehb54.quiz.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.ehb54.quiz.R
import io.github.ehb54.quiz.data.loadImage
import io.github.ehb54.quiz.model.Card
import io.github.ehb54.quiz.model.Direction
import io.github.ehb54.quiz.model.Progress
import io.github.ehb54.quiz.model.QuizMode
import io.github.ehb54.quiz.model.isSound
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
fun dueText(progress: Progress): String? {
    val lowest = progress.lowest ?: return null
    return if (progress.due == 0) stringResource(R.string.cleared_to, lowest) else progress.due.toString()
}

/** A label with its due count: "Pick 2", "Pick ✓2", or just "Pick" when no card can be asked that way. */
@Composable
fun dueLabel(label: String, progress: Progress): String =
    dueText(progress)?.let { stringResource(R.string.label_due, label, it) } ?: label

/** Plays [file], or stops it while it plays. [large] for a prompt that is a sound. */
@Composable
fun SoundButton(file: File, modifier: Modifier = Modifier, large: Boolean = false) {
    val player = LocalSoundPlayer.current
    val playing = player.playing == file
    val icon = painterResource(if (playing) R.drawable.ic_stop else R.drawable.ic_sound)
    val description = stringResource(if (playing) R.string.stop_sound else R.string.play_sound)
    val onClick = { if (playing) player.stop() else player.play(file) }
    if (large) {
        FilledTonalIconButton(onClick = onClick, modifier = modifier.size(96.dp)) {
            Icon(icon, description, Modifier.size(48.dp))
        }
    } else {
        IconButton(onClick = onClick, modifier = modifier) { Icon(icon, description) }
    }
}

/**
 * A card's prompt in a session: its picture or its sound, then its text. A sound
 * plays by itself when [playKey] changes (a new card or round); [onListened]
 * runs when that playing ends, so an answer timer can start from there.
 */
@Composable
fun Prompt(card: Card, direction: Direction, mediaDir: File, pictureHeight: Dp, playKey: Any, onListened: () -> Unit) {
    val media = card.promptMedia(direction)
    if (media != null && isSound(media)) {
        val file = File(mediaDir, media)
        val player = LocalSoundPlayer.current
        val listened by rememberUpdatedState(onListened)
        LaunchedEffect(playKey) { player.play(file) { listened() } }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SoundButton(file, large = true) }
    } else if (media != null) {
        CardImage(
            File(mediaDir, media),
            Modifier
                .fillMaxWidth()
                .heightIn(max = pictureHeight),
        )
    }
    val prompt = card.prompt(direction)
    if (prompt.isNotBlank()) Text(prompt, style = MaterialTheme.typography.headlineMedium)
}

/** Tags a Pick choice with its card, so tests can find sounds, which look alike. */
fun choiceTag(id: Long) = "choice:$id"

/** Tags a Match tile with its card and column, for the same reason. */
fun tileTag(id: Long, sideA: Boolean) = if (sideA) "tile:a:$id" else "tile:b:$id"

/** A sound among Pick choices or Match tiles: its play button, with "Sound 2" beside it. */
@Composable
fun SoundTileContent(file: File, number: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SoundButton(file)
        Text(stringResource(R.string.sound_number, number), style = MaterialTheme.typography.titleMedium)
    }
}

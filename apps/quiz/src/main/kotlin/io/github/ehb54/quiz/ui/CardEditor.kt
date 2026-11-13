package io.github.ehb54.quiz.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ehb54.quiz.R
import io.github.ehb54.quiz.model.Card
import io.github.ehb54.quiz.model.isSound
import java.io.File

/**
 * Adds or edits a card. Each side is text, a picture or a sound. A picture or
 * a sound can't be typed, so a card with both sides as files is only asked in
 * Pick and Match. A picture or a sound may have its text (the word it says),
 * and a side shown as text or a picture a sound; both show only after the
 * answer. Editing keeps the card's stats.
 */
@Composable
fun CardEditor(
    card: Card?,
    mediaDir: File,
    onPickPicture: (Uri, (String?) -> Unit) -> Unit,
    onPickSound: (Uri, (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onSave: (Card) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var a by rememberSaveable { mutableStateOf(card?.a.orEmpty()) }
    var b by rememberSaveable { mutableStateOf(card?.b.orEmpty()) }
    var mediaA by rememberSaveable { mutableStateOf(card?.mediaA) }
    var mediaB by rememberSaveable { mutableStateOf(card?.mediaB) }
    var soundA by rememberSaveable { mutableStateOf(card?.soundA) }
    var soundB by rememberSaveable { mutableStateOf(card?.soundB) }
    var pickingA by rememberSaveable { mutableStateOf(true) }
    // Picking a sound for after the answer rather than the side's own file.
    var pickingAfter by rememberSaveable { mutableStateOf(false) }
    val stored: (String?) -> Unit = { name ->
        if (name != null) {
            when {
                pickingAfter && pickingA -> soundA = name
                pickingAfter -> soundB = name
                pickingA -> mediaA = name
                else -> mediaB = name
            }
        }
    }
    val picturePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPickPicture(uri, stored)
    }
    // Sounds come from files: recording would need the microphone permission.
    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onPickSound(uri, stored)
    }
    fun pickPicture(sideA: Boolean) {
        pickingA = sideA
        pickingAfter = false
        picturePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    fun pickSound(sideA: Boolean, after: Boolean = false) {
        pickingA = sideA
        pickingAfter = after
        soundPicker.launch(arrayOf("audio/*"))
    }
    val valid = (mediaA != null || a.isNotBlank()) && (mediaB != null || b.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (card == null) R.string.add_card else R.string.edit_card)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SideEditor(
                    label = R.string.side_a,
                    text = a,
                    onText = { a = it },
                    media = mediaA,
                    afterSound = soundA,
                    mediaDir = mediaDir,
                    onPickPicture = { pickPicture(true) },
                    onPickSound = { pickSound(true) },
                    onPickAfterSound = { pickSound(true, after = true) },
                    onRemoveAfterSound = { soundA = null },
                ) { mediaA = null }
                SideEditor(
                    label = R.string.side_b,
                    text = b,
                    onText = { b = it },
                    media = mediaB,
                    afterSound = soundB,
                    mediaDir = mediaDir,
                    onPickPicture = { pickPicture(false) },
                    onPickSound = { pickSound(false) },
                    onPickAfterSound = { pickSound(false, after = true) },
                    onRemoveAfterSound = { soundB = null },
                ) { mediaB = null }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // A side that is a sound needs no other sound for after the answer.
                    val edited = (card ?: Card(id = 0)).copy(
                        a = a.trim(),
                        b = b.trim(),
                        mediaA = mediaA,
                        mediaB = mediaB,
                        soundA = soundA.takeIf { !isSound(mediaA) },
                        soundB = soundB.takeIf { !isSound(mediaB) },
                    )
                    onSave(edited)
                },
                enabled = valid,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
private fun SideEditor(
    @StringRes label: Int,
    text: String,
    onText: (String) -> Unit,
    media: String?,
    afterSound: String?,
    mediaDir: File,
    onPickPicture: () -> Unit,
    onPickSound: () -> Unit,
    onPickAfterSound: () -> Unit,
    onRemoveAfterSound: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (media != null) {
            Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
            val sound = isSound(media)
            if (sound) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SoundButton(File(mediaDir, media))
                    Text(stringResource(R.string.sound))
                }
            } else {
                CardImage(
                    File(mediaDir, media),
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp),
                    maxSide = 640,
                )
            }
            TextButton(onClick = onRemove) {
                Text(stringResource(if (sound) R.string.remove_sound else R.string.remove_picture))
            }
            OutlinedTextField(
                value = text,
                onValueChange = onText,
                label = { Text(stringResource(R.string.file_text)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            OutlinedTextField(
                value = text,
                onValueChange = onText,
                label = { Text(stringResource(label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row {
                TextButton(onClick = onPickPicture) { Text(stringResource(R.string.use_picture)) }
                TextButton(onClick = onPickSound) { Text(stringResource(R.string.use_sound)) }
            }
        }
        if (!isSound(media)) {
            if (afterSound != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SoundButton(File(mediaDir, afterSound))
                    Text(stringResource(R.string.after_sound), Modifier.weight(1f))
                    TextButton(onClick = onRemoveAfterSound) { Text(stringResource(R.string.remove)) }
                }
            } else {
                TextButton(onClick = onPickAfterSound) { Text(stringResource(R.string.add_after_sound)) }
            }
        }
    }
}

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ehb54.quiz.R
import io.github.ehb54.quiz.model.Card
import java.io.File

/**
 * Adds or edits a card. Each side is text or a picture; only one side may be
 * a picture, since the other has to be typed. Editing keeps the card's stats.
 */
@Composable
fun CardEditor(
    card: Card?,
    imageDir: File,
    onPickPicture: (Uri, (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onSave: (Card) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var a by rememberSaveable { mutableStateOf(card?.a.orEmpty()) }
    var b by rememberSaveable { mutableStateOf(card?.b.orEmpty()) }
    var imageA by rememberSaveable { mutableStateOf(card?.imageA) }
    var imageB by rememberSaveable { mutableStateOf(card?.imageB) }
    var pickingA by rememberSaveable { mutableStateOf(true) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            onPickPicture(uri) { name ->
                if (name != null) {
                    if (pickingA) imageA = name else imageB = name
                }
            }
        }
    }
    fun pick(sideA: Boolean) {
        pickingA = sideA
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val valid = (imageA != null || a.isNotBlank()) && (imageB != null || b.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (card == null) R.string.add_card else R.string.edit_card)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SideEditor(R.string.side_a, a, { a = it }, imageA, imageDir, imageB == null, { pick(true) }) {
                    imageA = null
                }
                SideEditor(R.string.side_b, b, { b = it }, imageB, imageDir, imageA == null, { pick(false) }) {
                    imageB = null
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val edited = (card ?: Card(id = 0)).copy(
                        a = if (imageA != null) "" else a.trim(),
                        b = if (imageB != null) "" else b.trim(),
                        imageA = imageA,
                        imageB = imageB,
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
    image: String?,
    imageDir: File,
    canPick: Boolean,
    onPick: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (image != null) {
            Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
            CardImage(
                File(imageDir, image),
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 160.dp),
                maxSide = 640,
            )
            TextButton(onClick = onRemove) { Text(stringResource(R.string.remove_picture)) }
        } else {
            OutlinedTextField(
                value = text,
                onValueChange = onText,
                label = { Text(stringResource(label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (canPick) TextButton(onClick = onPick) { Text(stringResource(R.string.use_picture)) }
        }
    }
}

package io.github.ehb54.quiz.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ehb54.quiz.R
import io.github.ehb54.quiz.model.Card
import io.github.ehb54.quiz.model.Direction
import io.github.ehb54.quiz.model.Quiz
import io.github.ehb54.quiz.model.QuizMode
import io.github.ehb54.quiz.model.QuizSettings
import java.io.File
import io.github.ehb54.core.ui.R as CoreR

private enum class DetailDialog { None, Rename, Reset, Delete }

private val thresholds = (0..10).toList()
private val timerSteps = listOf(0, 5, 10, 15, 20, 30, 45, 60, 90, 120)

/** Match mode: pairs per block. */
private val blockSizes = (3..10).toList()

/** Pick mode: choices per card. */
private val choiceCounts = (2..8).toList()

private val modeLabels = mapOf(
    QuizMode.Typed to R.string.mode_type,
    QuizMode.Pick to R.string.mode_pick,
    QuizMode.Match to R.string.mode_match,
)

/** Cards per session when no range is set; 0 means all. */
private val sessionSizes = listOf(5, 10, 15, 20, 25, 30, 40, 50, 75, 100, 0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizDetailScreen(quiz: Quiz, vm: QuizViewModel, snackbar: SnackbarHostState) {
    /** Card being edited: null for none, 0 for a new card. */
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    var dialog by rememberSaveable { mutableStateOf(DetailDialog.None) }
    val exportText = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> if (uri != null) vm.exportQuiz(quiz.id, uri) }
    val exportZip = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> if (uri != null) vm.exportQuiz(quiz.id, uri) }

    BackHandler(onBack = vm::showList)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(quiz.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = vm::showList) {
                        Icon(painterResource(CoreR.drawable.core_ui_ic_back), stringResource(R.string.back))
                    }
                },
                actions = {
                    OverflowMenu(
                        listOf(
                            R.string.rename to { dialog = DetailDialog.Rename },
                            R.string.export to {
                                if (quiz.imageFiles().isEmpty()) {
                                    exportText.launch("${quiz.name}.qzx")
                                } else {
                                    exportZip.launch("${quiz.name}.zip")
                                }
                            },
                            R.string.reset_stats to { dialog = DetailDialog.Reset },
                            R.string.delete_quiz to { dialog = DetailDialog.Delete },
                        ),
                    )
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = 0L }) {
                Icon(painterResource(CoreR.drawable.core_ui_ic_add), stringResource(R.string.add_card))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { insets ->
        LazyColumn(contentPadding = insets) {
            item {
                SessionSettings(quiz, onChange = { vm.updateSettings(quiz.id, it) }, onStart = { vm.startQuiz(quiz.id) })
                HorizontalDivider()
            }
            itemsIndexed(quiz.cards, key = { _, card -> card.id }) { index, card ->
                CardRow(index + 1, card, quiz.level(card), vm.imageDir) { editing = card.id }
            }
            // Room to scroll the last card out from under the + button.
            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    editing?.let { id ->
        val card = quiz.card(id)
        CardEditor(
            card = card,
            imageDir = vm.imageDir,
            onPickPicture = vm::storePicture,
            onDismiss = { editing = null },
            onSave = {
                vm.saveCard(quiz.id, it)
                editing = null
            },
            onDelete = card?.let {
                {
                    vm.deleteCard(quiz.id, it.id)
                    editing = null
                }
            },
        )
    }

    when (dialog) {
        DetailDialog.None -> Unit
        DetailDialog.Rename -> NameDialog(R.string.rename, quiz.name, onDismiss = { dialog = DetailDialog.None }) {
            vm.renameQuiz(quiz.id, it)
            dialog = DetailDialog.None
        }
        DetailDialog.Reset -> ConfirmDialog(
            stringResource(R.string.reset_stats_confirm, quiz.name),
            R.string.reset_stats,
            onDismiss = { dialog = DetailDialog.None },
        ) {
            vm.resetStats(quiz.id)
            dialog = DetailDialog.None
        }
        DetailDialog.Delete -> ConfirmDialog(
            stringResource(R.string.delete_quiz_confirm, quiz.name),
            R.string.delete_quiz,
            onDismiss = { dialog = DetailDialog.None },
        ) {
            dialog = DetailDialog.None
            vm.deleteQuiz(quiz.id)
        }
    }
}

@Composable
private fun SessionSettings(quiz: Quiz, onChange: (QuizSettings) -> Unit, onStart: () -> Unit) {
    val settings = quiz.settings
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuizMode.entries.forEach { mode ->
                FilterChip(
                    selected = settings.mode == mode,
                    onClick = { onChange(settings.copy(mode = mode)) },
                    label = { Text(stringResource(modeLabels.getValue(mode))) },
                )
            }
        }
        // Match pairs both sides at once; the other modes ask one way round.
        if (settings.mode != QuizMode.Match) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Direction.entries.forEach { direction ->
                    FilterChip(
                        selected = settings.direction == direction,
                        onClick = { onChange(settings.copy(direction = direction)) },
                        label = {
                            Text(stringResource(if (direction == Direction.AToB) R.string.direction_ab else R.string.direction_ba))
                        },
                    )
                }
            }
        }
        Stepper(
            stringResource(R.string.threshold),
            settings.threshold,
            thresholds,
            settings.threshold.toString(),
        ) { onChange(settings.copy(threshold = it)) }
        if (settings.mode == QuizMode.Match) {
            Stepper(
                stringResource(R.string.block_size),
                settings.blockSize,
                blockSizes,
                settings.blockSize.toString(),
            ) { onChange(settings.copy(blockSize = it)) }
        } else {
            if (settings.mode == QuizMode.Pick) {
                Stepper(
                    stringResource(R.string.choices),
                    settings.choices,
                    choiceCounts,
                    settings.choices.toString(),
                ) { onChange(settings.copy(choices = it)) }
            }
            Stepper(
                stringResource(R.string.timer),
                settings.timerSeconds,
                timerSteps,
                if (settings.timerSeconds == 0) {
                    stringResource(R.string.timer_off)
                } else {
                    stringResource(R.string.seconds, settings.timerSeconds)
                },
            ) { onChange(settings.copy(timerSeconds = it)) }
        }
        Row(
            Modifier.toggleable(
                value = settings.shuffle,
                role = Role.Switch,
                onValueChange = { onChange(settings.copy(shuffle = it)) },
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.shuffle), Modifier.weight(1f))
            Switch(checked = settings.shuffle, onCheckedChange = null)
        }
        Stepper(
            stringResource(R.string.session_size),
            settings.sessionSize,
            sessionSizes,
            if (settings.sessionSize <= 0) stringResource(R.string.all_cards) else settings.sessionSize.toString(),
        ) { onChange(settings.copy(sessionSize = it)) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.range), Modifier.weight(1f))
            NumberField(settings.rangeFrom, "1", R.string.range_from) { onChange(settings.copy(rangeFrom = it)) }
            NumberField(settings.rangeTo, quiz.cards.size.toString(), R.string.range_to) {
                onChange(settings.copy(rangeTo = it))
            }
        }
        val asked = quiz.sessionCards().size
        val due = quiz.due().size
        Button(onClick = onStart, enabled = asked > 0, modifier = Modifier.fillMaxWidth()) {
            Text(pluralStringResource(R.plurals.start_due, asked, asked))
        }
        if (asked < due) {
            Text(
                pluralStringResource(R.plurals.due_in_all, due, due),
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun NumberField(value: Int?, placeholder: String, @StringRes label: Int, onChange: (Int?) -> Unit) {
    OutlinedTextField(
        value = value?.toString().orEmpty(),
        onValueChange = { text -> onChange(text.filter(Char::isDigit).take(5).toIntOrNull()?.takeIf { it > 0 }) },
        label = { Text(stringResource(label)) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(96.dp),
    )
}

@Composable
private fun CardRow(number: Int, card: Card, level: Int, imageDir: File, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Text(number.toString(), style = MaterialTheme.typography.labelMedium) },
        headlineContent = { Side(card.a, card.imageA, imageDir) },
        supportingContent = { Side(card.b, card.imageB, imageDir) },
        trailingContent = { Text(stringResource(R.string.level, level)) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun Side(text: String, image: String?, imageDir: File) {
    if (image != null) {
        CardImage(File(imageDir, image), Modifier.height(48.dp), maxSide = 192)
    } else {
        Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

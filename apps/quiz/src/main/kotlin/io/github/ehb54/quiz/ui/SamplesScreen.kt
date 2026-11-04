package io.github.ehb54.quiz.ui

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.ehb54.quiz.R
import io.github.ehb54.quiz.engine.SampleEntry
import io.github.ehb54.quiz.engine.SampleFile
import io.github.ehb54.quiz.engine.SampleStatus
import io.github.ehb54.quiz.engine.sampleStatus
import io.github.ehb54.quiz.model.Quiz
import io.github.ehb54.core.ui.R as CoreR

/**
 * The sample library: every sample quiz on the download site (or built into
 * the app), whether it is added here, and buttons to add, update or remove it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SamplesScreen(state: UiState, vm: QuizViewModel, snackbar: SnackbarHostState) {
    val library = state.library
    /** Source of the sample whose removal waits for confirmation. */
    var removing by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(onBack = vm::showList)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.samples_title)) },
                navigationIcon = {
                    IconButton(onClick = vm::showList) {
                        Icon(painterResource(CoreR.drawable.core_ui_ic_back), stringResource(R.string.back))
                    }
                },
                actions = {
                    OverflowMenu(
                        listOf(
                            R.string.check_updates to vm::refreshLibrary,
                            R.string.add_all to vm::addAllSamples,
                            R.string.update_all to vm::updateAllSamples,
                        ),
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { insets ->
        LazyColumn(contentPadding = insets) {
            item {
                val status = when {
                    library.loading -> R.string.library_loading
                    library.catalog == null -> R.string.library_none
                    library.online -> R.string.library_online
                    else -> R.string.library_offline
                }
                Text(
                    stringResource(status),
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                HorizontalDivider()
            }
            library.catalog?.files.orEmpty().forEach { file ->
                item(key = "file:${file.file}") { FileHeader(file) }
                items(file.quizzes, key = { it.source }) { entry ->
                    SampleRow(
                        entry = entry,
                        added = state.quizFrom(entry.source),
                        busy = entry.source in library.busy,
                        onAdd = { vm.addSample(entry.source) },
                        onUpdate = { vm.updateSample(entry.source) },
                        onRemove = { removing = entry.source },
                    )
                    HorizontalDivider()
                }
            }
            // Room to scroll the last sample out from under a snackbar.
            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    removing?.let { source ->
        val name = state.quizFrom(source)?.name
        if (name == null) {
            removing = null
        } else {
            ConfirmDialog(
                stringResource(R.string.remove_sample_confirm, name),
                R.string.remove,
                onDismiss = { removing = null },
            ) {
                removing = null
                vm.removeSample(source)
            }
        }
    }
}

/** A sample file's name, and whether adding its quizzes needs a download. */
@Composable
private fun FileHeader(file: SampleFile) {
    val where = if (file.builtIn) {
        stringResource(R.string.sample_built_in)
    } else {
        stringResource(R.string.sample_download, Formatter.formatShortFileSize(LocalContext.current, file.bytes))
    }
    Text(
        stringResource(R.string.two_parts, file.file, where),
        Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SampleRow(
    entry: SampleEntry,
    added: Quiz?,
    busy: Boolean,
    onAdd: () -> Unit,
    onUpdate: () -> Unit,
    onRemove: () -> Unit,
) {
    val status = sampleStatus(entry, added)
    val cards = pluralStringResource(R.plurals.card_count, entry.cards, entry.cards)
    // "26 cards · 26 sounds": pictures and sounds are what make a sample large.
    var size = cards
    for ((count, plural) in listOf(entry.pictures to R.plurals.picture_count, entry.sounds to R.plurals.sound_count)) {
        if (count > 0) size = stringResource(R.string.two_parts, size, pluralStringResource(plural, count, count))
    }
    val note = when {
        status == SampleStatus.UpdateAvailable -> stringResource(R.string.sample_update_available)
        added == null -> null
        added.name != entry.name -> stringResource(R.string.sample_added_as, added.name)
        else -> stringResource(R.string.sample_added)
    }
    ListItem(
        // One node for the name and its notes; the buttons stay separate.
        modifier = Modifier.semantics(mergeDescendants = true) {},
        headlineContent = { Text(entry.name) },
        supportingContent = { Text(if (note != null) stringResource(R.string.two_parts, size, note) else size) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    busy -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    status == SampleStatus.NotAdded -> SampleButton(R.string.add, R.string.add_named, entry.name, onAdd)
                    else -> {
                        if (status == SampleStatus.UpdateAvailable) {
                            SampleButton(R.string.update, R.string.update_named, entry.name, onUpdate)
                        }
                        SampleButton(R.string.remove, R.string.remove_named, entry.name, onRemove)
                    }
                }
            }
        },
    )
}

/** A text button whose spoken label names the sample ("Add Shapes"). */
@Composable
private fun SampleButton(@StringRes label: Int, @StringRes spoken: Int, name: String, onClick: () -> Unit) {
    val description = stringResource(spoken, name)
    TextButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = description }) {
        Text(stringResource(label))
    }
}

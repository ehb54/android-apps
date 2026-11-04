package io.github.ehb54.quiz.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ehb54.core.ui.BuildInfo
import io.github.ehb54.core.ui.BuildInfoCard
import io.github.ehb54.quiz.R
import io.github.ehb54.quiz.model.Quiz
import io.github.ehb54.quiz.model.QuizMode
import io.github.ehb54.core.ui.R as CoreR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizListScreen(
    quizzes: List<Quiz>,
    snackbar: SnackbarHostState,
    info: BuildInfo,
    onOpen: (String) -> Unit,
    onCreate: (String) -> Unit,
    onImport: (Uri) -> Unit,
    onSamples: () -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var about by rememberSaveable { mutableStateOf(false) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImport(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    OverflowMenu(
                        listOf(
                            R.string.import_quiz to { importer.launch(arrayOf("*/*")) },
                            R.string.sample_quizzes to onSamples,
                            R.string.about to { about = true },
                        ),
                    )
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(painterResource(CoreR.drawable.core_ui_ic_add), stringResource(R.string.new_quiz))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { insets ->
        if (quizzes.isEmpty()) {
            Text(
                stringResource(R.string.no_quizzes),
                Modifier
                    .padding(insets)
                    .padding(24.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            LazyColumn(contentPadding = insets) {
                items(quizzes, key = { it.id }) { quiz ->
                    ListItem(
                        headlineContent = { Text(quiz.name) },
                        supportingContent = { Text(cardsAndDue(quiz)) },
                        modifier = Modifier.clickable { onOpen(quiz.id) },
                    )
                    HorizontalDivider()
                }
                // Room to scroll the last quiz out from under the + button.
                item { Spacer(Modifier.height(88.dp)) }
            }
        }
    }

    if (creating) {
        NameDialog(R.string.new_quiz, "", onDismiss = { creating = false }) {
            creating = false
            onCreate(it)
        }
    }
    if (about) {
        AlertDialog(
            onDismissRequest = { about = false },
            title = { Text(stringResource(R.string.about)) },
            text = { BuildInfoCard(info) },
            confirmButton = { TextButton(onClick = { about = false }) { Text(stringResource(R.string.ok)) } },
        )
    }
}

@Composable
fun cardsAndDue(quiz: Quiz): String {
    // What is due in each mode, Type and Pick in the quiz's current direction:
    // "14 cards · Type 14 · Pick 2 · Match ✓".
    val cards = pluralStringResource(R.plurals.card_count, quiz.cards.size, quiz.cards.size)
    return QuizMode.entries.fold(cards) { line, mode ->
        stringResource(R.string.two_parts, line, dueLabel(stringResource(modeLabel(mode)), quiz.due(mode).size))
    }
}

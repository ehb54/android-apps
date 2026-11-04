package io.github.ehb54.quiz.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.ehb54.quiz.R
import io.github.ehb54.quiz.engine.MatchSession
import io.github.ehb54.quiz.model.Card
import io.github.ehb54.quiz.model.Quiz
import io.github.ehb54.quiz.model.isSound
import java.io.File

private val TEXT_TILE = 80.dp
private val PICTURE_TILE = 112.dp

/**
 * A block of tiles in two columns, side A on the left and side B on the right,
 * each shuffled: tap one on each side to pair them. Paired tiles leave a gap,
 * so the others stay put; a wrong pair is marked until the next tap. When the
 * block is cleared the next one appears. A sound tile has a button to play
 * it; a tap elsewhere on it picks it. Back does not leave.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchScreen(
    quiz: Quiz,
    session: MatchSession,
    mismatch: Pair<Long, Long>?,
    mediaDir: File,
    snackbar: SnackbarHostState,
    vm: QuizViewModel,
) {
    BackHandler(onBack = vm::backDuringQuiz)
    // A half-made pair belongs to its block.
    var pickedLeft by rememberSaveable(session.round, session.left) { mutableStateOf<Long?>(null) }
    var pickedRight by rememberSaveable(session.round, session.left) { mutableStateOf<Long?>(null) }
    fun pick(left: Long?, right: Long?) {
        vm.clearMismatch()
        val l = left ?: pickedLeft
        val r = right ?: pickedRight
        if (l != null && r != null) {
            pickedLeft = null
            pickedRight = null
            vm.pair(l, r)
        } else {
            pickedLeft = l
            pickedRight = r
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(quiz.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = { RoundCounter(session.round, session.remaining, vm::abandonSession) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { insets ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(insets)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(if (mismatch != null) R.string.not_a_pair else R.string.match_hint),
                color = if (mismatch != null) MaterialTheme.colorScheme.error else Color.Unspecified,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TileColumn(
                    tiles = session.left,
                    matched = session.matchedLeft,
                    picked = pickedLeft,
                    wrong = mismatch?.first,
                    quiz = quiz,
                    sideA = true,
                    mediaDir = mediaDir,
                    onPick = { id -> if (id == pickedLeft) pickedLeft = null else pick(id, null) },
                    modifier = Modifier.weight(1f),
                )
                TileColumn(
                    tiles = session.right,
                    matched = session.matchedRight,
                    picked = pickedRight,
                    wrong = mismatch?.second,
                    quiz = quiz,
                    sideA = false,
                    mediaDir = mediaDir,
                    onPick = { id -> if (id == pickedRight) pickedRight = null else pick(null, id) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TileColumn(
    tiles: List<Long>,
    matched: Set<Long>,
    picked: Long?,
    wrong: Long?,
    quiz: Quiz,
    sideA: Boolean,
    mediaDir: File,
    onPick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    fun Card.text() = if (sideA) a else b
    fun Card.media() = if (sideA) mediaA else mediaB
    val pictures = tiles.any { id -> quiz.card(id)?.media()?.let { !isSound(it) } == true }
    val height = if (pictures) PICTURE_TILE else TEXT_TILE
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.forEachIndexed { index, id ->
            val card = quiz.card(id)
            if (card == null || id in matched) {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(height),
                )
            } else {
                Tile(
                    text = card.text(),
                    media = card.media(),
                    mediaDir = mediaDir,
                    number = index + 1,
                    selected = id == picked,
                    wrong = id == wrong,
                    height = height,
                    modifier = Modifier.testTag(tileTag(id, sideA)),
                ) { onPick(id) }
            }
        }
    }
}

@Composable
private fun Tile(
    text: String,
    media: String?,
    mediaDir: File,
    number: Int,
    selected: Boolean,
    wrong: Boolean,
    height: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .selectable(selected = selected, role = Role.Button, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = when {
            wrong -> colors.errorContainer
            selected -> colors.primaryContainer
            else -> colors.surfaceVariant
        },
        border = BorderStroke(
            if (selected || wrong) 2.dp else 1.dp,
            when {
                wrong -> colors.error
                selected -> colors.primary
                else -> colors.outlineVariant
            },
        ),
    ) {
        Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
            if (media != null && isSound(media)) {
                SoundTileContent(File(mediaDir, media), number)
            } else if (media != null) {
                CardImage(File(mediaDir, media), Modifier.fillMaxSize(), maxSide = 512)
            } else {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

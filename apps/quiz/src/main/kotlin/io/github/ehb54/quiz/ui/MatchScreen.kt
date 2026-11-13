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
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.delay
import java.io.File

private val TEXT_TILE = 80.dp
private val PICTURE_TILE = 112.dp

/** How long a pair just made shows what its card shows once paired, before its tiles go. */
private const val PAIR_SHOWN_MS = 500L

/**
 * A block of tiles in two columns, side A on the left and side B on the right,
 * each shuffled: tap one on each side to pair them. Paired tiles leave a gap,
 * so the others stay put; a wrong pair is marked until the next tap. When the
 * block is cleared the next one appears. A sound tile plays when tapped, and is
 * picked; tapped again once picked, it only plays again. A right pair whose
 * card shows more once paired (a file's text, a sound) shows it on its tiles,
 * and plays the sound, for a moment before they go. Back does not leave.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchScreen(
    quiz: Quiz,
    session: MatchSession,
    mismatch: Pair<Long, Long>?,
    paired: Pair<Long, Long>?,
    mediaDir: File,
    snackbar: SnackbarHostState,
    vm: QuizViewModel,
) {
    BackHandler(onBack = vm::backDuringQuiz)
    val player = LocalSoundPlayer.current
    if (paired != null) {
        val (left, right) = paired
        val card = quiz.card(left)
        val sound = card?.sound(sideA = true) ?: card?.sound(sideA = false)
        if (sound != null) PlayOnce("$left:$right", File(mediaDir, sound))
        LaunchedEffect(paired) {
            delay(PAIR_SHOWN_MS)
            vm.showedPair()
        }
    }
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
    fun tap(id: Long, sideA: Boolean) {
        val card = quiz.card(id) ?: return
        val sound = (if (sideA) card.mediaA else card.mediaB)?.takeIf(::isSound)
        if (sound != null) player.play(File(mediaDir, sound))
        val picked = if (sideA) pickedLeft else pickedRight
        when {
            id != picked -> if (sideA) pick(id, null) else pick(null, id)
            // A picked sound tile only plays again; any other picked tile is let go.
            sound != null -> Unit
            sideA -> pickedLeft = null
            else -> pickedRight = null
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
                    shown = paired?.first,
                    quiz = quiz,
                    sideA = true,
                    mediaDir = mediaDir,
                    onTap = { id -> tap(id, sideA = true) },
                    modifier = Modifier.weight(1f),
                )
                TileColumn(
                    tiles = session.right,
                    matched = session.matchedRight,
                    picked = pickedRight,
                    wrong = mismatch?.second,
                    shown = paired?.second,
                    quiz = quiz,
                    sideA = false,
                    mediaDir = mediaDir,
                    onTap = { id -> tap(id, sideA = false) },
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
    shown: Long?,
    quiz: Quiz,
    sideA: Boolean,
    mediaDir: File,
    onTap: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                    card = card,
                    sideA = sideA,
                    mediaDir = mediaDir,
                    number = index + 1,
                    selected = id == picked,
                    wrong = id == wrong,
                    shown = id == shown,
                    height = height,
                    modifier = Modifier.testTag(tileTag(id, sideA)),
                ) { onTap(id) }
            }
        }
    }
}

/** One side of [card] as a tile; [shown], with what it shows once paired (a file's text, a sound's sign). */
@Composable
private fun Tile(
    card: Card,
    sideA: Boolean,
    mediaDir: File,
    number: Int,
    selected: Boolean,
    wrong: Boolean,
    shown: Boolean,
    height: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val text = if (sideA) card.a else card.b
    val media = if (sideA) card.mediaA else card.mediaB
    val marked = selected || shown
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .selectable(selected = marked, enabled = !shown, role = Role.Button, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = when {
            wrong -> colors.errorContainer
            marked -> colors.primaryContainer
            else -> colors.surfaceVariant
        },
        border = BorderStroke(
            if (marked || wrong) 2.dp else 1.dp,
            when {
                wrong -> colors.error
                marked -> colors.primary
                else -> colors.outlineVariant
            },
        ),
    ) {
        Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
            when {
                shown -> ShownSide(card, sideA, mediaDir, number)
                media != null && isSound(media) -> SoundTileContent(File(mediaDir, media), number, button = false)
                media != null -> CardImage(File(mediaDir, media), Modifier.fillMaxSize(), maxSide = 512)
                else -> TileText(text)
            }
        }
    }
}

/** A side of a pair just made, whole: its picture, then its sound's sign and its text. */
@Composable
private fun ShownSide(card: Card, sideA: Boolean, mediaDir: File, number: Int) {
    val text = if (sideA) card.a else card.b
    val media = if (sideA) card.mediaA else card.mediaB
    val sound = card.sound(sideA)
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (media != null && !isSound(media)) {
            CardImage(
                File(mediaDir, media),
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                maxSide = 512,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (sound != null) SoundIcon(File(mediaDir, sound))
            when {
                text.isNotBlank() -> TileText(text)
                media != null && isSound(media) ->
                    Text(stringResource(R.string.sound_number, number), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun TileText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

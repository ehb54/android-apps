package io.github.ehb54.quiz.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ehb54.quiz.R
import io.github.ehb54.quiz.engine.LevelResult
import io.github.ehb54.quiz.engine.Session
import io.github.ehb54.quiz.model.Quiz

/**
 * Speed mode between levels: the level's times, then Go on (to the level
 * waiting behind this screen) or Stop. After Stop in the middle of a level it
 * shows that level so far, and Done ends the run. Back ends it too: everything
 * is saved, and starting again picks up where it stopped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedLevelScreen(quiz: Quiz, session: Session, result: LevelResult, vm: QuizViewModel) {
    BackHandler(onBack = vm::endSpeed)
    val direction = session.direction
    val next = session.speed ?: return
    Scaffold(topBar = { TopAppBar(title = { Text(quiz.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }) }) {
            insets ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(insets)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(if (result.stopped) R.string.level_stopped else R.string.level_done, result.level),
                style = MaterialTheme.typography.headlineMedium,
            )
            if (result.stopped) Text(stringResource(R.string.level_answered, result.answered, result.cards))
            Text(stringResource(R.string.summary_first_try, result.firstTry, result.cards))
            Text(pluralStringResource(R.plurals.summary_misses, result.misses, result.misses))
            if (result.firstTry > 0) {
                Text(compared(R.string.speed_average, R.string.speed_average_was, result.averageMillis, result.previousAverageMillis, result.level))
                Text(compared(R.string.speed_median, R.string.speed_median_was, result.medianMillis, result.previousMedianMillis, result.level))
                Text(stringResource(R.string.speed_range, seconds(result.fastestMillis), seconds(result.slowestMillis)))
                Text(stringResource(R.string.speed_slowest))
                result.slowest.forEach { time ->
                    quiz.card(time.card)?.let {
                        Text("• " + stringResource(R.string.card_time, cardName(it, direction), seconds(time.millis)))
                    }
                }
            }
            if (result.confused.isNotEmpty()) {
                Text(stringResource(R.string.speed_confused))
                result.confused.mapNotNull(quiz::card).forEach { Text("• " + cardName(it, direction)) }
            }
            if (result.stopped) {
                Button(onClick = vm::endSpeed, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.done_title)) }
            } else {
                if (next.limitMillis > 0) Text(stringResource(R.string.speed_limit, next.level, seconds(next.limitMillis)))
                Button(onClick = vm::continueSpeed, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.go_on, next.level))
                }
                OutlinedButton(onClick = vm::endSpeed, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.stop)) }
            }
        }
    }
}

/** "Average 1.4 s", or with the level before's: "Average 1.4 s (level 2: 1.9 s)". */
@Composable
private fun compared(@StringRes alone: Int, @StringRes was: Int, millis: Long, previousMillis: Long, level: Int) =
    if (previousMillis > 0) {
        stringResource(was, seconds(millis), level - 1, seconds(previousMillis))
    } else {
        stringResource(alone, seconds(millis))
    }

private fun seconds(millis: Long) = millis / 1000.0

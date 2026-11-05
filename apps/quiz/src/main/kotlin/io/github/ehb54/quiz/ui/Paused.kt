package io.github.ehb54.quiz.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ehb54.quiz.R

// Pausing a timed session (speed mode, or a timer): the clock stops and the
// card is hidden until Resume, so a pause is no time to study it. Time before
// the pause still counts. The app pauses by itself when it goes away (WhenAway).

/** The top bar's pause button. */
@Composable
fun PauseButton(onPause: () -> Unit) {
    IconButton(onClick = onPause) { Icon(painterResource(R.drawable.ic_pause), stringResource(R.string.pause)) }
}

/** In place of a paused session's card. */
@Composable
fun PausedPane(onResume: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.paused), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.paused_text))
        Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.resume)) }
    }
}

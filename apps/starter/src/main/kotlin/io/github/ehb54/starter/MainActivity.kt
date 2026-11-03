package io.github.ehb54.starter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.ehb54.core.ui.AppTheme
import io.github.ehb54.core.ui.BuildInfo
import io.github.ehb54.core.ui.BuildInfoCard

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val info = BuildInfo(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.BUILD_TIME)
        setContent {
            AppTheme {
                StarterScreen(info)
            }
        }
    }
}

@Composable
private fun StarterScreen(info: BuildInfo) {
    var taps by rememberSaveable { mutableIntStateOf(0) }
    Scaffold { insets ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(insets)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            BuildInfoCard(info)
            Button(onClick = { taps++ }) {
                Text(pluralStringResource(R.plurals.taps, taps, taps))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StarterScreenPreview() {
    AppTheme {
        StarterScreen(BuildInfo("0.1.0-preview", 1, "just now"))
    }
}

package io.github.ehb54.core.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Version details an app passes in from its own BuildConfig. */
data class BuildInfo(
    val versionName: String,
    val versionCode: Int,
    val buildTime: String,
)

/** e.g. "macOS 14.5 (x86_64), Java 21.0.5" */
fun systemSummary(
    os: String = System.getProperty("os.name"),
    version: String = System.getProperty("os.version"),
    arch: String = System.getProperty("os.arch"),
    java: String = System.getProperty("java.version"),
): String {
    val name = if (os.startsWith("Mac")) "macOS" else os
    return "$name $version ($arch), Java $java"
}

/** Shows which build is running and what it is running on. */
@Composable
fun BuildInfoCard(info: BuildInfo, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(R.string.core_ui_version, info.versionName, info.versionCode),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.core_ui_built, info.buildTime),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(systemSummary(), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

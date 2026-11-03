package io.github.ehb54.core.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** Version details an app passes in from its own BuildConfig. */
data class BuildInfo(
    val versionName: String,
    val versionCode: Int,
    val buildTime: String,
)

/** e.g. "Android 9 (API 28), samsung SM-G955U" */
fun deviceSummary(release: String, sdkInt: Int, manufacturer: String, model: String): String =
    "Android $release (API $sdkInt), $manufacturer $model"

/** Shows which build is installed and what it is running on. */
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
            Text(
                deviceSummary(Build.VERSION.RELEASE, Build.VERSION.SDK_INT, Build.MANUFACTURER, Build.MODEL),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

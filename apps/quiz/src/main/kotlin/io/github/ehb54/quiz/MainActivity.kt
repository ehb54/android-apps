package io.github.ehb54.quiz

import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import io.github.ehb54.core.ui.AppTheme
import io.github.ehb54.core.ui.BuildInfo
import io.github.ehb54.quiz.ui.QuizApp
import io.github.ehb54.quiz.ui.QuizViewModel

class MainActivity : ComponentActivity() {
    private val vm by lazy { ViewModelProvider(this)[QuizViewModel::class.java] }

    /** Issue reports waiting on the phone go as soon as the network reaches the internet. */
    private val network = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) runOnUiThread { vm.online() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // The volume keys set the volume of card sounds, even between sounds.
        volumeControlStream = AudioManager.STREAM_MUSIC
        val info = BuildInfo(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.BUILD_TIME)
        setContent {
            AppTheme {
                QuizApp(info, vm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(network)
    }

    override fun onStop() {
        getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(network)
        super.onStop()
    }
}

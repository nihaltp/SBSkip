package com.nihaltp.sbskip

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nihaltp.sbskip.data.repository.SettingsRepository
import com.nihaltp.sbskip.model.ThemeMode
import com.nihaltp.sbskip.navigation.AppNavGraph
import com.nihaltp.sbskip.navigation.ShareIntentEvent
import com.nihaltp.sbskip.ui.theme.SBSkipTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var settingsRepository: SettingsRepository

    private var shareEvent by mutableStateOf<ShareIntentEvent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) {
            runBlocking(Dispatchers.IO) {
                settingsRepository.update { current ->
                    current.copy(
                        keepTempFiles = true,
                        verboseLogging = true,
                    )
                }
            }
        }
        shareEvent = intent.toShareIntentEvent()
        setContent {
            val settingsState by settingsRepository.settings.collectAsState(initial = null)
            val darkTheme =
                when (settingsState?.themeMode) {
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                    else -> isSystemInDarkTheme()
                }
            val dynamicColor = settingsState?.dynamicColor ?: true

            SBSkipTheme(
                darkTheme = darkTheme,
                dynamicColor = dynamicColor,
            ) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    App(shareEvent = shareEvent)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        shareEvent = intent.toShareIntentEvent()
    }
}

@Composable
private fun App(shareEvent: ShareIntentEvent?) {
    AppNavGraph(shareEvent = shareEvent)
}

private fun Intent.toShareIntentEvent(): ShareIntentEvent? {
    if (action != Intent.ACTION_SEND) return null
    val currentType = type ?: return null
    return if (currentType == "text/plain") {
        val text = getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        if (text.isBlank()) null else ShareIntentEvent(text = text, token = SystemClock.elapsedRealtimeNanos())
    } else if (currentType.startsWith("video/") || currentType.startsWith("audio/")) {
        val fileUri = getParcelableExtra<android.os.Parcelable>(Intent.EXTRA_STREAM) as? Uri
        if (fileUri == null) null else ShareIntentEvent(fileUri = fileUri, token = SystemClock.elapsedRealtimeNanos())
    } else {
        null
    }
}

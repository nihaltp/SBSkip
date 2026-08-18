package com.nihaltp.sbskip

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.nihaltp.sbskip.data.repository.SettingsRepository
import com.nihaltp.sbskip.model.ThemeMode
import com.nihaltp.sbskip.navigation.AppNavGraph
import com.nihaltp.sbskip.navigation.ShareIntentEvent
import com.nihaltp.sbskip.ui.theme.SBSkipTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var settingsRepository: SettingsRepository

    private var shareEvent by mutableStateOf<ShareIntentEvent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) {
            runBlocking(Dispatchers.IO) {
                settingsRepository.update { current ->
                    current.copy(
                        verboseLogging = true,
                    )
                }
            }
        }
        shareEvent = intent.toShareIntentEvent()
        val initialSettings = runBlocking { settingsRepository.settings.first() }
        setContent {
            val settingsState by settingsRepository.settings.collectAsState(initial = initialSettings)
            val darkTheme =
                when (settingsState.themeMode) {
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                    else -> isSystemInDarkTheme()
                }
            val dynamicColor = settingsState.dynamicColor

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
        val fileUri =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                getParcelableExtra<android.os.Parcelable>(Intent.EXTRA_STREAM) as? Uri
            }
        if (fileUri == null) null else ShareIntentEvent(fileUri = fileUri, token = SystemClock.elapsedRealtimeNanos())
    } else {
        null
    }
}

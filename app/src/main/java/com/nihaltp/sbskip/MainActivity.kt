package com.nihaltp.sbskip

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nihaltp.sbskip.navigation.AppNavGraph
import com.nihaltp.sbskip.navigation.ShareIntentEvent
import com.nihaltp.sbskip.ui.theme.SBSkipTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var shareEvent by mutableStateOf<ShareIntentEvent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        shareEvent = intent.toShareIntentEvent()
        setContent {
            SBSkipTheme {
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
    if (type != "text/plain") return null
    val text = getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
    if (text.isBlank()) return null
    return ShareIntentEvent(text = text, token = SystemClock.elapsedRealtimeNanos())
}

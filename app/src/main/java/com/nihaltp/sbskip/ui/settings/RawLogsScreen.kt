package com.nihaltp.sbskip.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nihaltp.sbskip.R
import com.nihaltp.sbskip.util.ExportImportUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RawLogsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    var logRefreshKey by remember { mutableStateOf(0) }
    val rawLogsText = remember(logRefreshKey) { viewModel.getLogs() }
    val context = LocalContext.current

    // Launcher for saving logs to a file via SAF
    val saveLogsLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/plain"),
        ) { uri: Uri? ->
            uri?.let { fileUri ->
                CoroutineScope(Dispatchers.IO).launch {
                    val success = ExportImportUtil.writeTextToUri(context, fileUri, rawLogsText)
                    val message =
                        if (success) {
                            context.resources.getString(R.string.logs_exported_toast)
                        } else {
                            context.resources.getString(R.string.logs_export_failed_toast)
                        }
                    // Show toast on UI thread
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.logs_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText(context.resources.getString(R.string.logs_title), rawLogsText)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, context.resources.getString(R.string.logs_copied_toast), Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy))
                    }
                    IconButton(onClick = {
                        val intent =
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, context.resources.getString(R.string.logs_title))
                                putExtra(Intent.EXTRA_TEXT, rawLogsText)
                            }
                        val chooser =
                            Intent.createChooser(intent, context.resources.getString(R.string.logs_title)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        context.startActivity(chooser)
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = {
                        val filename = "sbskip_logs_${java.text.SimpleDateFormat(
                            "yyyyMMdd_HHmmss",
                            java.util.Locale.US,
                        ).format(java.util.Date())}.txt"
                        saveLogsLauncher.launch(filename)
                    }) {
                        Icon(Icons.Default.Download, contentDescription = stringResource(R.string.logs_export_button))
                    }
                    IconButton(onClick = {
                        viewModel.clearLogs()
                        logRefreshKey++
                        Toast.makeText(context, context.resources.getString(R.string.logs_cleared_toast), Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.clear))
                    }
                },
            )
        },
    ) { paddingValues ->
        SelectionContainer(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            Text(
                text = if (rawLogsText.isEmpty()) stringResource(R.string.logs_empty) else rawLogsText,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
            )
        }
    }
}

package com.nihaltp.sbskip.ui.onboarding

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nihaltp.sbskip.R
import com.nihaltp.sbskip.model.WatchlistFolder
import com.nihaltp.sbskip.util.AppLogger
import com.nihaltp.sbskip.util.PermissionHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val watchlistFolders by viewModel.watchlistFolders.collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { viewModel.stepCount })

    val filesPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
        ) { results ->
            val granted = PermissionHelper.hasFilesPermission(context)
            if (granted) {
                coroutineScope.launch {
                    pagerState.scrollToPage(pagerState.currentPage + 1)
                }
            } else {
                viewModel.markFilesDeniedPermanently()
            }
        }

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
        ) { results ->
            val granted = PermissionHelper.hasNotificationPermission(context)
            if (granted) {
                coroutineScope.launch {
                    pagerState.scrollToPage(pagerState.currentPage + 1)
                }
            } else {
                viewModel.markNotificationsDeniedPermanently()
            }
        }

    val watchlistFolderLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            uri?.let {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                } catch (e: Exception) {
                    AppLogger.error("OnboardingScreen", e, "Failed to take persistable URI permission")
                }
                val resolvedPath = resolveRelativePathFromUri(context, it)
                viewModel.addWatchlistFolder(resolvedPath, it)
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Box(Modifier) },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.onboarding_exit),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            StepIndicator(
                stepCount = viewModel.stepCount,
                modifier = Modifier.padding(16.dp),
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                when (page) {
                    0 ->
                        FilesStep(
                            deniedPermanently = state.filesDeniedPermanently,
                            onAllow = {
                                filesPermissionLauncher.launch(
                                    PermissionHelper.getRequiredFilesPermissions(),
                                )
                            },
                            onSkip = {
                                coroutineScope.launch {
                                    pagerState.scrollToPage(pagerState.currentPage + 1)
                                }
                            },
                        )
                    1 ->
                        NotificationsStep(
                            deniedPermanently = state.notificationsDeniedPermanently,
                            onAllow = {
                                notificationPermissionLauncher.launch(
                                    PermissionHelper.getRequiredNotificationPermissions(),
                                )
                            },
                            onSkip = {
                                coroutineScope.launch {
                                    pagerState.scrollToPage(pagerState.currentPage + 1)
                                }
                            },
                        )
                    2 ->
                        WatchlistStep(
                            watchlistFolders = watchlistFolders,
                            onAddFolder = { watchlistFolderLauncher.launch(null) },
                            onRemoveFolder = { viewModel.removeWatchlistFolder(it) },
                            onSkip = {
                                viewModel.completeOnboarding()
                                onExit()
                            },
                        )
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(
    stepCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until stepCount) {
            Spacer(
                modifier =
                    Modifier
                        .size(8.dp),
            )
            if (i < stepCount - 1) {
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }
}

@Composable
private fun FilesStep(
    deniedPermanently: Boolean,
    onAllow: () -> Unit,
    onSkip: () -> Unit,
) {
    StepContent(
        icon = { Text("Files") },
        title = stringResource(id = R.string.onboarding_step_files_title),
        description = stringResource(id = R.string.onboarding_step_files_desc),
        deniedPermanently = deniedPermanently,
        deniedMessage = stringResource(id = R.string.onboarding_step_files_denied),
        onAllow = onAllow,
        onSkip = onSkip,
    )
}

@Composable
private fun NotificationsStep(
    deniedPermanently: Boolean,
    onAllow: () -> Unit,
    onSkip: () -> Unit,
) {
    StepContent(
        icon = { Text("Notifications") },
        title = stringResource(id = R.string.onboarding_step_notifications_title),
        description = stringResource(id = R.string.onboarding_step_notifications_desc),
        deniedPermanently = deniedPermanently,
        deniedMessage = stringResource(id = R.string.onboarding_step_notifications_denied),
        onAllow = onAllow,
        onSkip = onSkip,
    )
}

@Composable
private fun WatchlistStep(
    watchlistFolders: List<WatchlistFolder>,
    onAddFolder: () -> Unit,
    onRemoveFolder: (WatchlistFolder) -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(id = R.string.onboarding_step_watchlist_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(id = R.string.onboarding_step_watchlist_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (watchlistFolders.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(id = R.string.onboarding_step_watchlist_added),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            watchlistFolders.forEach { folder ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = folder.path,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = { onRemoveFolder(folder) }) {
                        Text(stringResource(id = R.string.remove))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onAddFolder,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(id = R.string.onboarding_step_watchlist_add))
            }
            TextButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text =
                        stringResource(
                            id = if (watchlistFolders.isNotEmpty()) R.string.onboarding_continue else R.string.onboarding_skip,
                        ),
                )
            }
        }
    }
}

@Composable
private fun StepContent(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    deniedPermanently: Boolean,
    deniedMessage: String,
    onAllow: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon()
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (deniedPermanently) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = deniedMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!deniedPermanently) {
                Button(
                    onClick = onAllow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(id = R.string.onboarding_allow))
                }
            }
            TextButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(id = R.string.onboarding_skip))
            }
        }
    }
}

private fun resolveRelativePathFromUri(
    context: android.content.Context,
    uri: Uri,
): String {
    try {
        val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
        try {
            val docUri =
                android.provider.DocumentsContract.buildDocumentUriUsingTree(uri, docId)
            context.contentResolver
                .query(
                    docUri,
                    arrayOf(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                    null,
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index =
                            cursor.getColumnIndexOrThrow(
                                android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            )
                        if (index != -1) {
                            val dispName = cursor.getString(index)
                            if (!dispName.isNullOrBlank()) {
                                return "$dispName/"
                            }
                        }
                    }
                }
        } catch (_: Exception) {
            // Fallback below
        }
        val split = docId.split(":")
        val rawPath = if (split.size > 1) split[1] else docId
        val trimmedPath = rawPath.trim('/')
        return if (trimmedPath.isEmpty()) "SB Skip/" else "$trimmedPath/"
    } catch (_: Exception) {
        val path = uri.path ?: ""
        return if (path.isEmpty()) "SB Skip/" else "${path.trim('/')}/"
    }
}

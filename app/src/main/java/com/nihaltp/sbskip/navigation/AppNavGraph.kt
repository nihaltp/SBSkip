package com.nihaltp.sbskip.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nihaltp.sbskip.ui.main.MainScreen
import com.nihaltp.sbskip.ui.main.MainViewModel
import com.nihaltp.sbskip.ui.settings.SettingsScreen

@Composable
fun AppNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    shareEvent: ShareIntentEvent? = null,
) {
    val viewModel: MainViewModel = hiltViewModel()

    LaunchedEffect(shareEvent?.token) {
        shareEvent?.let(viewModel::handleSharedText)
    }

    val uiState by viewModel.uiState.collectAsState()

    NavHost(
        navController = navController,
        startDestination = Destination.Home.route,
        modifier = modifier,
    ) {
        composable(Destination.Home.route) {
            MainScreen(
                uiState = uiState,
                onUrlChange = viewModel::onUrlChanged,
                onFileSelected = viewModel::onFileSelected,
                onClearSelectedFile = viewModel::clearSelectedFile,
                onSubmit = viewModel::queueCurrentItem,
                onAutoDetectPending = viewModel::autoDetectAndClean,
                onCancelPending = viewModel::cancelPendingDownload,
                onConfirmPending = viewModel::confirmDetectedFile,
                onStartManualPickForPending = viewModel::startManualPickForPendingDownload,
                onConvertVideoToAudioChange = viewModel::onConvertVideoToAudioChanged,
                onDeleteOriginalVideoChange = viewModel::onDeleteOriginalVideoChanged,
                onOpenSettings = { navController.navigate(Destination.Settings.route) },
                onRemoveQueueItem = viewModel::removeQueueItem,
                onUndoRemoveQueueItem = viewModel::undoRemoveQueueItem,
                onDownloadQueueItem = viewModel::downloadQueueItemViaNewPipe,
                onDismissToast = viewModel::dismissToast,
                onRetryQueueItem = { id, bypass -> viewModel.retryQueueItem(id, bypass) },
                onSnackbarShown = viewModel::consumeSnackbarMessage,
                onProceedAnyway = viewModel::proceedWithMismatch,
                onCancelMismatchDialog = viewModel::dismissDurationMismatchDialog,
                onFindFile = viewModel::findFileForUrl,
                onCancelConflictDialog = viewModel::cancelConflictDialog,
                onReplaceConflict = viewModel::proceedConflictReplace,
                onRenameConflict = viewModel::proceedConflictRename,
                onDismissWatchlistPrompt = viewModel::dismissWatchlistPromptDialog,
                onCustomCategoriesChanged = viewModel::onCustomCategoriesChanged,
                onDismissPermissionRevokedDialog = viewModel::dismissPermissionRevokedDialog,
                onSearchNow = viewModel::triggerAutoDetectNow,
                onSkipPlaylistVideo = viewModel::skipPlaylistVideo,
                onDownloadPlaylistVideo = viewModel::downloadPlaylistVideo,
                onCancelPlaylistDownload = viewModel::cancelPlaylistDownload,
                onPlaylistConvertVideoToAudioChanged = viewModel::setPlaylistConvertVideoToAudio,
                onPlaylistDeleteOriginalVideoChanged = viewModel::setPlaylistDeleteOriginalVideo,
            )
        }

        composable(Destination.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToLicenses = { navController.navigate(Destination.Licenses.route) },
            )
        }

        composable(Destination.Licenses.route) {
            androidx.compose.material3.Scaffold(
                topBar = {
                    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                    androidx.compose.material3.TopAppBar(
                        title = {
                            androidx.compose.material3.Text(
                                androidx.compose.ui.res.stringResource(com.nihaltp.sbskip.R.string.licenses_title),
                            )
                        },
                        navigationIcon = {
                            androidx.compose.material3.IconButton(onClick = { navController.popBackStack() }) {
                                androidx.compose.material3.Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = androidx.compose.ui.res.stringResource(com.nihaltp.sbskip.R.string.back),
                                )
                            }
                        },
                    )
                },
            ) { paddingValues ->
                com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                )
            }
        }
    }
}

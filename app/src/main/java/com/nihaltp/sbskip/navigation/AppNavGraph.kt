package com.nihaltp.sbskip.navigation

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
                onAutoDetect = viewModel::autoDetectAndClean,
                onCancelPendingDownload = viewModel::cancelPendingDownload,
                onConfirmDetectedFile = viewModel::confirmDetectedFile,
                onOpenSettings = { navController.navigate(Destination.Settings.route) },
                onRemoveQueueItem = viewModel::removeQueueItem,
                onRetryQueueItem = viewModel::retryQueueItem,
                onSnackbarShown = viewModel::consumeSnackbarMessage,
            )
        }

        composable(Destination.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}

package com.nihaltp.sbskip.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nihaltp.sbskip.util.ImportManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Handles the "Cancel scan" action of the import notifications.
 *
 * The receiver is not exported, it is only reachable through the pending intent of the
 * notification.
 */
@AndroidEntryPoint
class ImportCancelReceiver : BroadcastReceiver() {
    @Inject
    lateinit var importManager: ImportManager

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        importManager.cancelCurrentScan()
    }
}

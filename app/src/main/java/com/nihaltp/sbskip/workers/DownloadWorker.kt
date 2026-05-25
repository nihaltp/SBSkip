package com.nihaltp.sbskip.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf

class DownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL).orEmpty()
        if (url.isBlank()) {
            return Result.failure(workDataOf(KEY_ERROR to "Missing URL"))
        }

        return Result.success(
            workDataOf(
                KEY_OUTPUT_PATH to "",
                KEY_MESSAGE to "Queue pipeline scaffolded; native downloader integration comes next.",
            ),
        )
    }

    companion object {
        const val KEY_URL = "url"
        const val KEY_ERROR = "error"
        const val KEY_OUTPUT_PATH = "output_path"
        const val KEY_MESSAGE = "message"
    }
}

package com.nihaltp.sbskip.ui.main

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nihaltp.sbskip.R
import com.nihaltp.sbskip.data.repository.QueueRepository
import com.nihaltp.sbskip.data.repository.SettingsRepository
import com.nihaltp.sbskip.model.DetectedFile
import com.nihaltp.sbskip.model.MainUiState
import com.nihaltp.sbskip.model.MediaType
import com.nihaltp.sbskip.model.PendingDownload
import com.nihaltp.sbskip.navigation.ShareIntentEvent
import com.nihaltp.sbskip.storage.DownloadStorage
import com.nihaltp.sbskip.util.YouTubeUrlParser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val queueRepository: QueueRepository,
    private val settingsRepository: SettingsRepository,
    private val downloadStorage: DownloadStorage,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            queueRepository.observeQueue().collect { queueItems ->
                _uiState.update { it.copy(queueItems = queueItems) }
            }
        }
        checkNewPipeInstalled()
    }

    fun checkNewPipeInstalled() {
        val installed = runCatching {
            context.packageManager.getPackageInfo(NEWPIPE_PACKAGE_NAME, 0)
        }.isSuccess
        _uiState.update { it.copy(isNewPipeInstalled = installed) }
    }

    fun onUrlChanged(value: String) {
        _uiState.update { it.copy(urlInput = value) }
    }

    fun onFileSelected(uri: Uri) {
        viewModelScope.launch {
            val metadata = downloadStorage.queryMetadata(uri.toString())
            val name = metadata?.let { "${it.title}.${it.extension}" } ?: context.getString(R.string.imported_file_fallback)
            val mediaType = if (metadata?.extension == "mp3" || metadata?.extension == "m4a" || metadata?.extension == "aac") {
                MediaType.AUDIO
            } else {
                MediaType.VIDEO
            }
            val settings = settingsRepository.settings.first()
            _uiState.update {
                it.copy(
                    selectedFileUri = uri.toString(),
                    selectedFileName = name,
                    selectedFileMediaType = mediaType,
                    convertVideoToAudio = settings.defaultConvertVideoToAudio,
                    deleteOriginalVideo = settings.defaultDeleteOriginalVideo,
                    detectedFile = null,
                    detectedFileName = null,
                )
            }
        }
    }

    fun clearSelectedFile() {
        _uiState.update {
            it.copy(
                selectedFileUri = null,
                selectedFileName = "",
                selectedFileMediaType = null,
                convertVideoToAudio = false,
                deleteOriginalVideo = true,
                detectedFile = null,
                detectedFileName = null,
            )
        }
    }

    fun onConvertVideoToAudioChanged(value: Boolean) {
        _uiState.update { it.copy(convertVideoToAudio = value) }
    }

    fun onDeleteOriginalVideoChanged(value: Boolean) {
        _uiState.update { it.copy(deleteOriginalVideo = value) }
    }

    fun queueCurrentItem() {
        viewModelScope.launch {
            queueCurrentItemInternal()
        }
    }

    fun startDownloadAndClean() {
        viewModelScope.launch {
            startDownloadAndCleanInternal()
        }
    }

    fun autoDetectAndClean() {
        viewModelScope.launch {
            autoDetectAndCleanInternal()
        }
    }

    fun confirmDetectedFile() {
        viewModelScope.launch {
            val state = uiState.value
            val detected = state.detectedFile ?: return@launch
            val detectedName = state.detectedFileName
            val metadata = downloadStorage.queryMetadata(detected.uri)
            val mediaType = if (metadata?.extension == "mp3" || metadata?.extension == "m4a" || metadata?.extension == "aac") {
                MediaType.AUDIO
            } else {
                MediaType.VIDEO
            }
            val settings = settingsRepository.settings.first()
            _uiState.update {
                it.copy(
                    selectedFileUri = detected.uri,
                    selectedFileName = detectedName ?: context.getString(R.string.detected_file_fallback),
                    selectedFileMediaType = mediaType,
                    convertVideoToAudio = settings.defaultConvertVideoToAudio,
                    deleteOriginalVideo = settings.defaultDeleteOriginalVideo,
                    detectedFile = null,
                    detectedFileName = null,
                )
            }
            queueCurrentItemInternal()
        }
    }

    fun cancelPendingDownload() {
        _uiState.update {
            it.copy(
                pendingDownload = null,
                detectedFile = null,
                detectedFileName = null,
                isFetchingMetadata = false,
                isDetectingFile = false,
            )
        }
    }

    fun findFileForUrl() {
        viewModelScope.launch {
            val state = uiState.value
            val inputUrl = state.urlInput.trim()
            val videoId = YouTubeUrlParser.extractVideoId(inputUrl)

            if (inputUrl.isBlank() || videoId.isNullOrBlank()) {
                _uiState.update { it.copy(snackbarMessage = context.getString(R.string.enter_valid_url)) }
                return@launch
            }

            _uiState.update { it.copy(isFetchingMetadata = true) }

            val normalizedUrl = "https://www.youtube.com/watch?v=$videoId"
            val metadata = runCatching { fetchYouTubeOEmbed(normalizedUrl) }.getOrElse {
                YouTubeMetadata(title = state.urlInput.ifBlank { videoId }, thumbnailUrl = null)
            }

            val pendingDownload = PendingDownload(
                videoId = videoId,
                url = normalizedUrl,
                title = metadata.title.ifBlank { videoId },
                thumbnailUrl = metadata.thumbnailUrl,
                createdAtEpochMillis = System.currentTimeMillis(),
            )

            _uiState.update {
                it.copy(
                    pendingDownload = pendingDownload,
                    detectedFile = null,
                    detectedFileName = null,
                    isFetchingMetadata = false,
                    snackbarMessage = null,
                )
            }

            autoDetectAndCleanInternal()
        }
    }

    fun handleSharedText(event: ShareIntentEvent) {
        if (event.text != null) {
            onUrlChanged(event.text)
        }
        if (event.fileUri != null) {
            onFileSelected(event.fileUri)
        }
    }

    fun retryQueueItem(id: Long) {
        viewModelScope.launch {
            val result = queueRepository.retry(id)
            _uiState.update { it.copy(snackbarMessage = result.message) }
        }
    }

    fun removeQueueItem(id: Long) {
        viewModelScope.launch {
            queueRepository.remove(id)
            _uiState.update { it.copy(snackbarMessage = context.getString(R.string.snackbar_item_removed)) }
        }
    }

    fun consumeSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun proceedWithMismatch() {
        val state = uiState.value
        val pending = state.pendingEnqueueData ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedFileUri = pending.fileUri,
                    selectedFileName = pending.title,
                )
            }
            queueCurrentItemInternal(force = true)
        }
    }

    fun dismissDurationMismatchDialog() {
        _uiState.update {
            it.copy(
                showDurationMismatchDialog = false,
                pendingEnqueueData = null,
            )
        }
    }

    private suspend fun queueCurrentItemInternal(force: Boolean = false) {
        val state = uiState.value
        val fileUri = state.selectedFileUri
        val youtubeUrl = state.pendingDownload?.url ?: state.urlInput.trim()

        if (fileUri.isNullOrBlank()) {
            if (youtubeUrl.isBlank()) {
                _uiState.update { it.copy(snackbarMessage = context.getString(R.string.snackbar_paste_first)) }
                return
            }

            if (!state.isNewPipeInstalled) {
                _uiState.update { it.copy(snackbarMessage = context.getString(R.string.newpipe_not_installed)) }
                return
            }

            startDownloadAndCleanInternal()
            return
        }

        val isConvertOnly = youtubeUrl.isBlank() && state.selectedFileMediaType == MediaType.VIDEO

        if (!isConvertOnly) {
            if (youtubeUrl.isBlank()) {
                _uiState.update { it.copy(snackbarMessage = context.getString(R.string.snackbar_paste_first)) }
                return
            }

            val videoId = YouTubeUrlParser.extractVideoId(youtubeUrl)
            if (videoId.isNullOrBlank()) {
                _uiState.update { it.copy(snackbarMessage = context.getString(R.string.enter_valid_url)) }
                return
            }

            val metadata = downloadStorage.queryMetadata(fileUri)
            val fileDuration = metadata?.durationSeconds ?: 0L

            if (!force) {
                _uiState.update { it.copy(isVerifyingDuration = true) }
                val youtubeDuration = com.nihaltp.sbskip.util.YouTubeDurationFetcher.fetchDuration(videoId)
                _uiState.update { it.copy(isVerifyingDuration = false) }

                if (youtubeDuration != null && fileDuration != youtubeDuration) {
                    val mediaType = if (state.convertVideoToAudio || metadata?.extension == "mp3" || metadata?.extension == "m4a" || metadata?.extension == "aac") {
                        MediaType.AUDIO
                    } else {
                        MediaType.VIDEO
                    }
                    val title = state.selectedFileName.ifBlank { state.pendingDownload?.title ?: metadata?.title ?: context.getString(R.string.imported_file_fallback) }
                    _uiState.update {
                        it.copy(
                            showDurationMismatchDialog = true,
                            mismatchFileDuration = fileDuration,
                            mismatchYoutubeDuration = youtubeDuration,
                            pendingEnqueueData = com.nihaltp.sbskip.model.PendingEnqueueData(
                                fileUri = fileUri,
                                title = title,
                                youtubeUrl = youtubeUrl,
                                mediaType = mediaType,
                            ),
                        )
                    }
                    return
                }
            }
        }

        val metadata = downloadStorage.queryMetadata(fileUri)
        val mediaType = if (isConvertOnly || state.convertVideoToAudio || metadata?.extension == "mp3" || metadata?.extension == "m4a" || metadata?.extension == "aac") {
            MediaType.AUDIO
        } else {
            MediaType.VIDEO
        }

        val title = state.selectedFileName.ifBlank { state.pendingDownload?.title ?: metadata?.title ?: context.getString(R.string.imported_file_fallback) }

        val finalUrl = if (isConvertOnly) {
            ""
        } else if (force) {
            if (youtubeUrl.contains("?")) "$youtubeUrl&bypassDurationCheck=true" else "$youtubeUrl?bypassDurationCheck=true"
        } else {
            youtubeUrl
        }

        val result = queueRepository.enqueue(
            localFileUri = fileUri,
            title = title,
            youtubeUrl = finalUrl,
            mediaType = mediaType,
            convertVideoToAudio = if (isConvertOnly) true else state.convertVideoToAudio,
            deleteOriginalVideo = state.deleteOriginalVideo,
        )

        _uiState.update {
            if (result.success) {
                it.copy(
                    urlInput = "",
                    selectedFileUri = null,
                    selectedFileName = "",
                    selectedFileMediaType = null,
                    convertVideoToAudio = false,
                    deleteOriginalVideo = true,
                    pendingDownload = null,
                    detectedFile = null,
                    detectedFileName = null,
                    showDurationMismatchDialog = false,
                    pendingEnqueueData = null,
                    snackbarMessage = context.getString(R.string.snackbar_media_enqueued),
                )
            } else {
                it.copy(snackbarMessage = result.message)
            }
        }
    }

    private suspend fun startDownloadAndCleanInternal() {
        val state = uiState.value
        val inputUrl = state.urlInput.trim()
        val videoId = YouTubeUrlParser.extractVideoId(inputUrl)

        if (inputUrl.isBlank() || videoId.isNullOrBlank()) {
            _uiState.update { it.copy(snackbarMessage = context.getString(R.string.enter_valid_url)) }
            return
        }

        if (!state.isNewPipeInstalled) {
            _uiState.update { it.copy(snackbarMessage = context.getString(R.string.newpipe_not_installed)) }
            return
        }

        _uiState.update { it.copy(isFetchingMetadata = true) }

        val normalizedUrl = "https://www.youtube.com/watch?v=$videoId"
        val metadata = runCatching { fetchYouTubeOEmbed(normalizedUrl) }.getOrElse {
            YouTubeMetadata(title = state.urlInput.ifBlank { videoId }, thumbnailUrl = null)
        }

        val pendingDownload = PendingDownload(
            videoId = videoId,
            url = normalizedUrl,
            title = metadata.title.ifBlank { videoId },
            thumbnailUrl = metadata.thumbnailUrl,
            createdAtEpochMillis = System.currentTimeMillis(),
        )

        _uiState.update {
            it.copy(
                pendingDownload = pendingDownload,
                detectedFile = null,
                detectedFileName = null,
                isFetchingMetadata = false,
                snackbarMessage = null,
            )
        }

        launchNewPipe(normalizedUrl)
    }

    private suspend fun autoDetectAndCleanInternal() {
        val state = uiState.value
        val pendingDownload = state.pendingDownload
            ?: run {
                _uiState.update { it.copy(snackbarMessage = context.getString(R.string.no_pending_download)) }
                return
            }

        _uiState.update { it.copy(isDetectingFile = true, detectedFile = null, detectedFileName = null) }

        val settings = settingsRepository.settings.first()
        val candidates = withContext(Dispatchers.IO) {
            collectRecentCandidates(pendingDownload, settings)
        }
        val bestCandidate = candidates.maxByOrNull { it.score }

        if (bestCandidate == null || bestCandidate.score < MIN_CONFIDENCE_SCORE) {
            _uiState.update {
                it.copy(
                    isDetectingFile = false,
                    snackbarMessage = context.getString(R.string.no_matching_download_found),
                )
            }
            return
        }

        val detectedName = withContext(Dispatchers.IO) {
            readDisplayName(bestCandidate.uri) ?: bestCandidate.fallbackName
        }

        _uiState.update {
            it.copy(
                isDetectingFile = false,
                detectedFile = DetectedFile(uri = bestCandidate.uri, score = bestCandidate.score),
                detectedFileName = detectedName,
                snackbarMessage = context.getString(R.string.found_matching_file, bestCandidate.score),
            )
        }
    }

    private suspend fun fetchYouTubeOEmbed(videoUrl: String): YouTubeMetadata = withContext(Dispatchers.IO) {
        val oEmbedUrl = videoUrl.toHttpUrlOrNull()
            ?.newBuilder()
            ?.scheme("https")
            ?.host("www.youtube.com")
            ?.encodedPath("/oembed")
            ?.addQueryParameter("url", videoUrl)
            ?.addQueryParameter("format", "json")
            ?.build()
            ?: throw IOException(context.getString(R.string.unable_fetch_metadata))

        val request = Request.Builder().url(oEmbedUrl).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("oEmbed request failed: ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            val parsed = json.decodeFromString(YouTubeOEmbedResponse.serializer(), body)
            YouTubeMetadata(
                title = parsed.title,
                thumbnailUrl = parsed.thumbnailUrl,
            )
        }
    }

    private fun launchNewPipe(youtubeUrl: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(youtubeUrl)).apply {
            setPackage(NEWPIPE_PACKAGE_NAME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(intent)
        }.onFailure {
            _uiState.update { state ->
                state.copy(snackbarMessage = context.getString(R.string.newpipe_launch_failed))
            }
        }
    }

    private suspend fun collectRecentCandidates(
        pendingDownload: PendingDownload,
        settings: com.nihaltp.sbskip.model.AppSettings,
    ): List<DetectedCandidate> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cutoffMillis = now - RECENT_WINDOW_MILLIS
        val resolver = context.contentResolver
        val candidates = mutableListOf<DetectedCandidate>()

        listOf(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
        ).forEach { collectionUri ->
            queryCandidates(
                resolver = resolver,
                collectionUri = collectionUri,
                pendingDownload = pendingDownload,
                settings = settings,
                cutoffMillis = cutoffMillis,
                output = candidates,
            )
        }

        candidates.sortedByDescending { it.score }
    }

    private fun queryCandidates(
        resolver: ContentResolver,
        collectionUri: Uri,
        pendingDownload: PendingDownload,
        settings: com.nihaltp.sbskip.model.AppSettings,
        cutoffMillis: Long,
        output: MutableList<DetectedCandidate>,
    ) {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DURATION,
        )

        val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        resolver.query(collectionUri, projection, null, null, sortOrder)?.use { cursor ->
            val idIndex = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
            val displayNameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val relativePathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            val dateAddedIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
            val dateModifiedIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
            val durationIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)

            var processed = 0
            while (cursor.moveToNext() && processed < MAX_SCAN_RESULTS_PER_COLLECTION) {
                processed++
                val id = if (idIndex != -1) cursor.getLong(idIndex) else continue
                val displayName = if (displayNameIndex != -1) cursor.getString(displayNameIndex).orEmpty() else ""
                val relativePath = if (relativePathIndex != -1) cursor.getString(relativePathIndex).orEmpty() else ""
                val dateAddedSeconds = if (dateAddedIndex != -1) cursor.getLong(dateAddedIndex) else 0L
                val dateModifiedSeconds = if (dateModifiedIndex != -1) cursor.getLong(dateModifiedIndex) else 0L
                val durationSeconds = if (durationIndex != -1) cursor.getLong(durationIndex) else null

                val timestampMillis = when {
                    dateModifiedSeconds > 0 -> dateModifiedSeconds * 1000L
                    dateAddedSeconds > 0 -> dateAddedSeconds * 1000L
                    else -> 0L
                }

                if (timestampMillis in 1 until cutoffMillis) {
                    continue
                }

                val uri = ContentUris.withAppendedId(collectionUri, id).toString()
                val score = scoreCandidate(
                    pendingDownload = pendingDownload,
                    displayName = displayName,
                    relativePath = relativePath,
                    durationSeconds = durationSeconds,
                    timestampMillis = timestampMillis,
                    settings = settings,
                )

                output += DetectedCandidate(
                    uri = uri,
                    score = score,
                    fallbackName = displayName.ifBlank { uri.substringAfterLast('/') },
                )
            }
        }
    }

    private fun scoreCandidate(
        pendingDownload: PendingDownload,
        displayName: String,
        relativePath: String,
        durationSeconds: Long?,
        timestampMillis: Long,
        settings: com.nihaltp.sbskip.model.AppSettings,
    ): Int {
        val normalizedTitle = normalizeText(pendingDownload.title)
        val normalizedCandidate = normalizeText(stripExtension(displayName))
        var score = 0

        score += titleSimilarityScore(normalizedTitle, normalizedCandidate)

        if (pendingDownload.videoId.isNotBlank() && displayName.contains(pendingDownload.videoId, ignoreCase = true)) {
            score += 20
        }

        if (folderHintMatches(relativePath, settings.newPipeVideoFolder) || folderHintMatches(relativePath, settings.newPipeAudioFolder)) {
            score += 18
        }

        val ageMillis = kotlin.math.abs(timestampMillis - pendingDownload.createdAtEpochMillis)
        score += when {
            ageMillis <= 2 * 60 * 1000L -> 22
            ageMillis <= 5 * 60 * 1000L -> 18
            ageMillis <= 10 * 60 * 1000L -> 12
            ageMillis <= 15 * 60 * 1000L -> 8
            else -> 0
        }

        if (durationSeconds != null && pendingDownload.title.isNotBlank()) {
            // We do not have the exact expected duration from oEmbed, but very short files are unlikely to match long YouTube titles.
            if (durationSeconds >= 30L) {
                score += 4
            }
        }

        return score.coerceIn(0, 100)
    }

    private fun titleSimilarityScore(expectedTitle: String, candidateTitle: String): Int {
        if (expectedTitle.isBlank() || candidateTitle.isBlank()) return 0

        if (candidateTitle == expectedTitle) {
            return 60
        }

        if (candidateTitle.contains(expectedTitle) || expectedTitle.contains(candidateTitle)) {
            return 48
        }

        val expectedWords = expectedTitle.split(WORD_SPLIT_REGEX).filter { it.isNotBlank() }.toSet()
        val candidateWords = candidateTitle.split(WORD_SPLIT_REGEX).filter { it.isNotBlank() }.toSet()
        if (expectedWords.isEmpty() || candidateWords.isEmpty()) {
            return 0
        }

        val overlap = expectedWords.intersect(candidateWords).size.toDouble()
        val total = expectedWords.union(candidateWords).size.toDouble()
        val jaccard = overlap / total
        return (jaccard * 60.0).toInt()
    }

    private fun folderHintMatches(relativePath: String, folderHint: String): Boolean {
        if (folderHint.isBlank()) return false
        val normalizedPath = normalizeText(relativePath)
        val normalizedHint = normalizeText(folderHint)
        return normalizedPath.contains(normalizedHint)
    }

    private fun normalizeText(value: String): String {
        return value
            .lowercase(Locale.ROOT)
            .replace(WORD_SPLIT_REGEX, " ")
            .trim()
    }

    private fun stripExtension(displayName: String): String {
        val dotIndex = displayName.lastIndexOf('.')
        return if (dotIndex > 0) displayName.substring(0, dotIndex) else displayName
    }

    private fun readDisplayName(uriString: String): String? {
        val uri = Uri.parse(uriString)
        val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            if (index != -1 && cursor.moveToFirst()) {
                return cursor.getString(index)
            }
        }
        return null
    }

    private data class DetectedCandidate(
        val uri: String,
        val score: Int,
        val fallbackName: String,
    )

    @Serializable
    private data class YouTubeOEmbedResponse(
        val title: String,
        @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    )

    private data class YouTubeMetadata(
        val title: String,
        val thumbnailUrl: String?,
    )

    companion object {
        private const val NEWPIPE_PACKAGE_NAME = "org.schabi.newpipe"
        private const val RECENT_WINDOW_MILLIS = 15 * 60 * 1000L
        private const val MIN_CONFIDENCE_SCORE = 55
        private const val MAX_SCAN_RESULTS_PER_COLLECTION = 100
        private val WORD_SPLIT_REGEX = Regex("[^a-z0-9]+")
    }
}

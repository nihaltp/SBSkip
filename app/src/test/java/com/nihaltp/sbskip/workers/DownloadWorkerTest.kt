package com.nihaltp.sbskip.workers

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nihaltp.sbskip.data.repository.QueueRepository
import com.nihaltp.sbskip.data.repository.SettingsRepository
import com.nihaltp.sbskip.model.AppSettings
import com.nihaltp.sbskip.model.DownloadQueueItem
import com.nihaltp.sbskip.model.DownloadQueueStatus
import com.nihaltp.sbskip.model.DownloadSource
import com.nihaltp.sbskip.model.MediaFileMetadata
import com.nihaltp.sbskip.model.MediaType
import com.nihaltp.sbskip.model.ProcessingPlan
import com.nihaltp.sbskip.model.SponsorBlockSegment
import com.nihaltp.sbskip.model.YouTubeMetadata
import com.nihaltp.sbskip.notifications.DownloadNotificationManager
import com.nihaltp.sbskip.util.ytdlp.YtDlpCommandBuilder
import com.nihaltp.sbskip.util.ytdlp.YtDlpPaths
import com.nihaltp.sbskip.util.ytdlp.YtDlpProcessRunner
import com.nihaltp.sbskip.util.ytdlp.YtDlpRunResult
import com.nihaltp.sbskip.util.ytdlp.YtDlpRuntime
import com.nihaltp.sbskip.workers.helpers.DurationValidator
import com.nihaltp.sbskip.workers.helpers.MediaProcessingManager
import com.nihaltp.sbskip.workers.helpers.MediaTagger
import com.nihaltp.sbskip.workers.helpers.MetadataFetcher
import com.nihaltp.sbskip.workers.helpers.OutputSaver
import com.nihaltp.sbskip.workers.helpers.ProcessingPlanBuilder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DownloadWorkerTest {
    private lateinit var context: Context
    private lateinit var queueRepository: QueueRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var notificationManager: DownloadNotificationManager
    private lateinit var workScheduler: DownloadWorkScheduler
    private lateinit var metadataFetcher: MetadataFetcher
    private lateinit var durationValidator: DurationValidator
    private lateinit var processingPlanBuilder: ProcessingPlanBuilder
    private lateinit var mediaProcessingManager: MediaProcessingManager
    private lateinit var mediaTagger: MediaTagger
    private lateinit var outputSaver: OutputSaver
    private lateinit var ytDlpRuntime: YtDlpRuntime
    private lateinit var ytDlpCommandBuilder: YtDlpCommandBuilder
    private lateinit var ytDlpProcessRunner: YtDlpProcessRunner

    private lateinit var worker: DownloadWorker

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        every { context.cacheDir } returns File(System.getProperty("java.io.tmpdir"), "sbskip-test")
        every { context.getString(any()) } returns ""

        queueRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        notificationManager = mockk(relaxed = true)
        workScheduler = mockk(relaxed = true)
        metadataFetcher = mockk(relaxed = true)
        durationValidator = mockk(relaxed = true)
        processingPlanBuilder = mockk(relaxed = true)
        mediaProcessingManager = mockk(relaxed = true)
        mediaTagger = mockk(relaxed = true)
        outputSaver = mockk(relaxed = true)
        ytDlpRuntime = mockk(relaxed = true)
        ytDlpCommandBuilder = mockk(relaxed = true)
        ytDlpProcessRunner = mockk(relaxed = true)

        every { settingsRepository.settings } returns flowOf(AppSettings())
        every { notificationManager.createForegroundInfo(any(), any(), any()) } answers {
            throw RuntimeException("foreground not available in tests")
        }
        coEvery { queueRepository.findItemById(any()) } returns null
        coEvery { queueRepository.markFetchingSegments(any()) } returns Unit
        coEvery { metadataFetcher.fetchVideoDuration(any()) } returns null
        coEvery { metadataFetcher.fetchYouTubeMetadata(any()) }
            .returns(YouTubeMetadata(title = "Test", authorName = "Author", authorUrl = "", thumbnailUrl = null))
        coEvery { metadataFetcher.fetchSponsorSegments(any(), any()) } returns emptyList<SponsorBlockSegment>()
        coEvery { metadataFetcher.checkApiStatus() } returns "operational"
        coEvery { queueRepository.updateMetadata(any(), any(), any(), any()) } returns Unit
        coEvery { queueRepository.markProcessing(any()) } returns Unit
        coEvery { processingPlanBuilder.createPlan(any(), any(), any(), any()) }
            .returns(ProcessingPlan(emptyList(), false, "mp4"))
        coEvery { mediaProcessingManager.process(any(), any(), any(), any(), any()) }
            .returns(File(System.getProperty("java.io.tmpdir"), "out.mp4"))
        coEvery { mediaTagger.applyMetadata(any(), any()) }
            .returns(File(System.getProperty("java.io.tmpdir"), "out.mp4"))
        coEvery { outputSaver.save(any(), any()) } returns "content://media/saved.mp4"
        coEvery { queueRepository.markCompleted(any(), any(), any()) } returns Unit
        coEvery { queueRepository.markFailed(any(), any()) } returns Unit

        // yt-dlp runtime seams
        coEvery { ytDlpRuntime.ensure(any()) } returns
            YtDlpPaths(
                baseDir = File(System.getProperty("java.io.tmpdir"), "ytdlp-base"),
                pythonRoot = File(System.getProperty("java.io.tmpdir"), "ytdlp-python"),
                ffmpegRoot = File(System.getProperty("java.io.tmpdir"), "ytdlp-ffmpeg"),
                pythonLauncher = File("/system/lib/libpython.so"),
                ffmpegLauncher = File("/system/lib/libffmpeg.so"),
                ffprobeLauncher = File("/system/lib/libffprobe.so"),
                launcherDir = File("/system/lib"),
                zipapp = File(System.getProperty("java.io.tmpdir"), "ytdlp"),
            )
        every { ytDlpRuntime.workDir(any(), any()) } returns File(System.getProperty("java.io.tmpdir"), "ytdlp-work")
        every { ytDlpRuntime.environment(any(), any()) } returns emptyMap()

        val params = mockk<WorkerParameters>(relaxed = true)
        every { params.inputData } returns workDataOf(DownloadWorker.KEY_QUEUE_ITEM_ID to 1L)
        worker =
            DownloadWorker(
                appContext = context,
                params = params,
                queueRepository = queueRepository,
                settingsRepository = settingsRepository,
                notificationManager = notificationManager,
                workScheduler = workScheduler,
                metadataFetcher = metadataFetcher,
                durationValidator = durationValidator,
                processingPlanBuilder = processingPlanBuilder,
                mediaProcessingManager = mediaProcessingManager,
                mediaTagger = mediaTagger,
                outputSaver = outputSaver,
                ytDlpRuntime = ytDlpRuntime,
                ytDlpCommandBuilder = ytDlpCommandBuilder,
                ytDlpProcessRunner = ytDlpProcessRunner,
            )
    }

    @After
    fun teardown() {
    }

    private fun ytDlpQueueItem(
        id: Long = 1L,
        downloadSource: DownloadSource = DownloadSource.YTDLP,
        url: String = "https://youtube.com/watch?v=dQw4w9WgXcQ",
        localFileUri: String = "",
    ): DownloadQueueItem =
        DownloadQueueItem(
            id = id,
            url = url,
            title = "Test Video",
            localFileUri = localFileUri,
            mediaType = MediaType.VIDEO,
            thumbnailUrl = null,
            durationSeconds = null,
            status = DownloadQueueStatus.QUEUED,
            downloadSource = downloadSource,
            createdAtEpochMillis = System.currentTimeMillis(),
            updatedAtEpochMillis = System.currentTimeMillis(),
            errorMessage = null,
            outputPath = null,
            outputDurationSeconds = null,
            convertVideoToAudio = false,
            deleteOriginalVideo = true,
            audioOutputDirUri = null,
            videoOutputDirUri = null,
            relativePath = null,
            overwrite = false,
            bypassDurationCheck = false,
            noSuffix = false,
        )

    @Test
    fun `yt-dlp path calls yt-dlp runtime, command builder, and process runner`() =
        runTest {
            val item = ytDlpQueueItem()
            coEvery { queueRepository.findItemById(any()) } returns item

            val paths = mockk<YtDlpPaths>(relaxed = true)
            every { paths.ffmpegLauncher } returns File("/system/lib/ffmpeg")
            every { paths.pythonLauncher } returns File("/system/lib/python")
            coEvery { ytDlpRuntime.ensure(any()) } returns paths
            coEvery { ytDlpRuntime.environment(any(), any()) } returns mapOf("HOME" to "/tmp")
            every { ytDlpCommandBuilder.build(any()) } returns listOf("yt-dlp", "--no-playlist", item.url)
            coEvery { ytDlpProcessRunner.run(any(), any(), any()) }
                .returns(
                    YtDlpRunResult(
                        exitCode = 0,
                        stdout = "",
                        stderrTail = "",
                        lastProgress = null,
                    ),
                )
            // yt-dlp output file exists (workDir is mocked to /tmp/ytdlp-work)
            val workDir = File(System.getProperty("java.io.tmpdir"), "ytdlp-work")
            workDir.mkdirs()
            File(workDir, "out.mp4").createNewFile()

            worker.doWork()

            coVerify { ytDlpRuntime.ensure() }
            verify { ytDlpCommandBuilder.build(any()) }
            coVerify { ytDlpProcessRunner.run(any(), any(), any()) }
            coVerify { queueRepository.markDownloading(item.id) }
            coVerify { queueRepository.markProcessing(item.id) }
            coVerify { queueRepository.markCompleted(item.id, any(), any()) }
        }

    @Test
    fun `yt-dlp path skips local file duration validation`() =
        runTest {
            val item = ytDlpQueueItem(localFileUri = "")
            coEvery { queueRepository.findItemById(any()) } returns item

            val paths = mockk<YtDlpPaths>(relaxed = true)
            every { paths.ffmpegLauncher } returns File("/system/lib/ffmpeg")
            coEvery { ytDlpRuntime.ensure(any()) } returns paths
            coEvery { ytDlpRuntime.environment(any(), any()) } returns emptyMap()
            every { ytDlpCommandBuilder.build(any()) } returns listOf("yt-dlp")
            coEvery { ytDlpProcessRunner.run(any(), any(), any()) }
                .returns(
                    YtDlpRunResult(
                        exitCode = 0,
                        stdout = "",
                        stderrTail = "",
                        lastProgress = null,
                    ),
                )
            // yt-dlp output file exists (workDir is mocked to /tmp/ytdlp-work)
            val workDir = File(System.getProperty("java.io.tmpdir"), "ytdlp-work")
            workDir.mkdirs()
            File(workDir, "out.mp4").createNewFile()

            worker.doWork()

            coVerify(exactly = 0) { durationValidator.validate(any(), any(), any()) }
            coVerify { ytDlpRuntime.ensure() }
        }

    @Test
    fun `yt-dlp download failure marks item failed`() =
        runTest {
            val item = ytDlpQueueItem()
            coEvery { queueRepository.findItemById(any()) } returns item

            val paths = mockk<YtDlpPaths>(relaxed = true)
            every { paths.ffmpegLauncher } returns File("/system/lib/ffmpeg")
            coEvery { ytDlpRuntime.ensure(any()) } returns paths
            coEvery { ytDlpRuntime.environment(any(), any()) } returns emptyMap()
            every { ytDlpCommandBuilder.build(any()) } returns listOf("yt-dlp")
            coEvery { ytDlpProcessRunner.run(any(), any(), any()) }
                .returns(
                    YtDlpRunResult(
                        exitCode = 1,
                        stdout = "",
                        stderrTail = "ERROR: Unable to extract video ID",
                        lastProgress = null,
                    ),
                )

            // yt-dlp output file exists (workDir is mocked to /tmp/ytdlp-work)
            val workDir = File(System.getProperty("java.io.tmpdir"), "ytdlp-work")
            workDir.mkdirs()
            File(workDir, "out.mp4").createNewFile()

            worker.doWork()

            coVerify { queueRepository.markFailed(item.id, any()) }
            coVerify(exactly = 0) { mediaProcessingManager.process(any(), any(), any(), any(), any()) }
        }

    @Test
    fun `non-yt-dlp path does not call yt-dlp components`() =
        runTest {
            val item =
                ytDlpQueueItem(
                    downloadSource = DownloadSource.LOCAL_FILE,
                    localFileUri = "content://media/local.mp4",
                )
            coEvery { queueRepository.findItemById(any()) } returns item
            coEvery { durationValidator.validate(any(), any(), any()) }
                .returns(MediaFileMetadata(title = "Test", extension = "mp4", durationSeconds = 100L))

            worker.doWork()

            coVerify(exactly = 0) { ytDlpRuntime.ensure() }
            verify(exactly = 0) { ytDlpCommandBuilder.build(any()) }
            coVerify(exactly = 0) { ytDlpProcessRunner.run(any(), any(), any()) }
        }

    @Test
    fun `yt-dlp path skips sponsorblock api fetch`() =
        runTest {
            val item = ytDlpQueueItem()
            coEvery { queueRepository.findItemById(any()) } returns item

            val paths = mockk<YtDlpPaths>(relaxed = true)
            every { paths.ffmpegLauncher } returns File("/system/lib/ffmpeg")
            every { paths.pythonLauncher } returns File("/system/lib/python")
            coEvery { ytDlpRuntime.ensure(any()) } returns paths
            coEvery { ytDlpRuntime.environment(any(), any()) } returns emptyMap()
            every { ytDlpCommandBuilder.build(any()) } returns listOf("yt-dlp", "--no-playlist", item.url)
            coEvery { ytDlpProcessRunner.run(any(), any(), any()) }
                .returns(
                    YtDlpRunResult(
                        exitCode = 0,
                        stdout = "",
                        stderrTail = "",
                        lastProgress = null,
                    ),
                )
            val workDir = File(System.getProperty("java.io.tmpdir"), "ytdlp-work")
            workDir.mkdirs()
            File(workDir, "out.mp4").createNewFile()

            worker.doWork()

            // SponsorBlock API should NOT be called for yt-dlp
            coVerify(exactly = 0) { metadataFetcher.fetchSponsorSegments(any(), any()) }
        }

    @Test
    fun `yt-dlp path skips ffmpeg processing`() =
        runTest {
            val item = ytDlpQueueItem()
            coEvery { queueRepository.findItemById(any()) } returns item

            val paths = mockk<YtDlpPaths>(relaxed = true)
            every { paths.ffmpegLauncher } returns File("/system/lib/ffmpeg")
            every { paths.pythonLauncher } returns File("/system/lib/python")
            coEvery { ytDlpRuntime.ensure(any()) } returns paths
            coEvery { ytDlpRuntime.environment(any(), any()) } returns emptyMap()
            every { ytDlpCommandBuilder.build(any()) } returns listOf("yt-dlp", "--no-playlist", item.url)
            coEvery { ytDlpProcessRunner.run(any(), any(), any()) }
                .returns(
                    YtDlpRunResult(
                        exitCode = 0,
                        stdout = "",
                        stderrTail = "",
                        lastProgress = null,
                    ),
                )
            val workDir = File(System.getProperty("java.io.tmpdir"), "ytdlp-work")
            workDir.mkdirs()
            File(workDir, "out.mp4").createNewFile()

            worker.doWork()

            // FFmpeg processing should NOT be called for yt-dlp
            coVerify(exactly = 0) { mediaProcessingManager.process(any(), any(), any(), any(), any()) }
        }

    @Test
    fun `yt-dlp path skips media tagger`() =
        runTest {
            val item = ytDlpQueueItem()
            coEvery { queueRepository.findItemById(any()) } returns item

            val paths = mockk<YtDlpPaths>(relaxed = true)
            every { paths.ffmpegLauncher } returns File("/system/lib/ffmpeg")
            every { paths.pythonLauncher } returns File("/system/lib/python")
            coEvery { ytDlpRuntime.ensure(any()) } returns paths
            coEvery { ytDlpRuntime.environment(any(), any()) } returns emptyMap()
            every { ytDlpCommandBuilder.build(any()) } returns listOf("yt-dlp", "--no-playlist", item.url)
            coEvery { ytDlpProcessRunner.run(any(), any(), any()) }
                .returns(
                    YtDlpRunResult(
                        exitCode = 0,
                        stdout = "",
                        stderrTail = "",
                        lastProgress = null,
                    ),
                )
            val workDir = File(System.getProperty("java.io.tmpdir"), "ytdlp-work")
            workDir.mkdirs()
            File(workDir, "out.mp4").createNewFile()

            worker.doWork()

            // MediaTagger should NOT be called for yt-dlp
            coVerify(exactly = 0) { mediaTagger.applyMetadata(any(), any()) }
        }

    @Test
    fun `non-yt-dlp path calls sponsorblock api ffmpeg and tagger`() =
        runTest {
            val item =
                ytDlpQueueItem(
                    downloadSource = DownloadSource.LOCAL_FILE,
                    localFileUri = "content://media/local.mp4",
                )
            coEvery { queueRepository.findItemById(any()) } returns item
            coEvery { durationValidator.validate(any(), any(), any()) }
                .returns(MediaFileMetadata(title = "Test", extension = "mp4", durationSeconds = 100L))

            worker.doWork()

            // Non-yt-dlp SHOULD call SponsorBlock API, FFmpeg, and MediaTagger
            coVerify(atLeast = 1) { metadataFetcher.fetchSponsorSegments(any(), any()) }
            coVerify(atLeast = 1) { mediaProcessingManager.process(any(), any(), any(), any(), any()) }
            coVerify(atLeast = 1) { mediaTagger.applyMetadata(any(), any()) }
        }
}

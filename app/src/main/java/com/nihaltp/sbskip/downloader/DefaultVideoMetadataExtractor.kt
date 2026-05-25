package com.nihaltp.sbskip.downloader

import com.nihaltp.sbskip.model.VideoMetadata
import com.nihaltp.sbskip.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultVideoMetadataExtractor @Inject constructor(
    private val ytDlpExecutor: YtDlpExecutor,
    private val fallbackExtractor: YoutubeMetadataFallbackExtractor,
) : VideoMetadataExtractor {
    override suspend fun extract(url: String): VideoMetadata {
        return runCatching {
            ytDlpExecutor.dumpSingleJson(url).toVideoMetadata()
        }.getOrElse { throwable ->
            AppLogger.metadata("yt-dlp metadata failed, falling back: ${throwable.message}")
            fallbackExtractor.extract(url)
        }
    }

    private fun YtDlpExecutionResult.toVideoMetadata(): VideoMetadata {
        val title = jsonValue("title") ?: throw IllegalStateException()
        val thumbnailUrl = jsonValue("thumbnail")
        val durationSeconds = jsonValue("duration")?.toLongOrNull()
        return VideoMetadata(title = title, thumbnailUrl = thumbnailUrl, durationSeconds = durationSeconds)
    }

    private fun YtDlpExecutionResult.jsonValue(name: String): String? {
        val match = Regex("\"$name\"\\s*:\\s*(\"((?:\\\\.|[^\\\"])*)\"|[-0-9.]+|null)")
            .find(output)
            ?: return null
        val raw = match.groupValues[1]
        return when {
            raw == "null" -> null
            raw.startsWith('"') -> raw.substring(1, raw.length - 1)
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\t", "\t")
            else -> raw
        }
    }
}

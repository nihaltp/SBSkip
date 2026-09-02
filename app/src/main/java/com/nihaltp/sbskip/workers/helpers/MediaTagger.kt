package com.nihaltp.sbskip.workers.helpers

import android.content.Context
import android.media.MediaMetadataRetriever
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.nihaltp.sbskip.BuildConfig
import com.nihaltp.sbskip.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class MediaTagger
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val coverArtManager: CoverArtManager,
    ) {
        suspend fun applyMetadata(
            inputFile: File,
            processingContext: ProcessingContext,
        ): File {
            if (!inputFile.exists()) return inputFile

            val videoId = processingContext.videoId ?: return inputFile
            val youtubeUrl = processingContext.queueItem.url
            val youtubeTitle =
                processingContext.oembedTitle
                    ?: processingContext.queueItem.title.ifBlank {
                        processingContext.localMetadata.title
                    }
            val authorName = processingContext.authorName
            val authorUrl = processingContext.authorUrl
            val thumbnailUrl = processingContext.thumbnailUrl
            val categories = resolveCategoriesForMetadata(processingContext)
            val sbSkipSegments = processingContext.sbSkipSegments

            val extension = inputFile.extension.lowercase()
            if (extension !in setOf("mp4", "m4a", "mp3", "m4v", "mov")) {
                return inputFile
            }

            val metadataJson =
                buildProcessedMetadataJson(
                    videoId = videoId,
                    youtubeUrl = youtubeUrl,
                    youtubeTitle = youtubeTitle,
                    authorName = authorName,
                    authorUrl = authorUrl,
                    thumbnailUrl = thumbnailUrl,
                    categories = categories,
                    sbSkipSegments = sbSkipSegments,
                )
            val taggedOutput = File(inputFile.parentFile, "${inputFile.nameWithoutExtension}_tagged.${inputFile.extension}")

            val isAudio = extension in setOf("m4a", "mp3")
            var thumbFile: File? = null
            if (isAudio && !coverArtManager.audioHasCoverImage(inputFile)) {
                val artworkUrlToDownload =
                    processingContext.musicMetadata?.artworkUrl?.takeIf { it.isNotBlank() }
                        ?: thumbnailUrl?.takeIf { it.isNotBlank() }
                if (artworkUrlToDownload != null) {
                    AppLogger.worker("Audio cover image is missing; downloading cover art: $artworkUrlToDownload")
                    val cacheDir = inputFile.parentFile ?: context.cacheDir
                    thumbFile = coverArtManager.downloadThumbnail(artworkUrlToDownload, cacheDir)

                    if (thumbFile == null && processingContext.musicMetadata?.artworkUrl != null) {
                        val fallbackThumbUrl = thumbnailUrl?.takeIf { it.isNotBlank() }
                        if (fallbackThumbUrl != null) {
                            AppLogger.worker("Artwork download failed, falling back to thumbnail: $fallbackThumbUrl")
                            thumbFile = coverArtManager.downloadThumbnail(fallbackThumbUrl, cacheDir)
                        }
                    }
                }
            }

            val existingMetadata = getExistingMetadata(inputFile)
            val metadataArgs = mutableListOf<String>()

            // Priority: Existing reliable metadata -> MusicMetadata -> YouTube basic metadata -> Filename
            val finalTitle =
                existingMetadata.title
                    ?: processingContext.musicMetadata?.title
                    ?: youtubeTitle

            val finalArtist =
                existingMetadata.artist
                    ?: processingContext.musicMetadata?.artists?.joinToString(", ")
                    ?: authorName

            val finalAlbum = existingMetadata.album ?: processingContext.musicMetadata?.album
            val finalYear = existingMetadata.year ?: processingContext.musicMetadata?.year?.toString()
            val finalGenre = existingMetadata.genre ?: processingContext.musicMetadata?.genre

            if (finalTitle.isNotBlank()) {
                metadataArgs.add("-metadata title=\"${escapeForFfmpeg(finalTitle)}\"")
            }

            if (!finalArtist.isNullOrBlank()) {
                if (isAudio) {
                    metadataArgs.add("-metadata artist=\"${escapeForFfmpeg(finalArtist)}\"")
                } else {
                    metadataArgs.add("-metadata author=\"${escapeForFfmpeg(finalArtist)}\"")
                }
            }

            if (!finalAlbum.isNullOrBlank()) {
                metadataArgs.add("-metadata album=\"${escapeForFfmpeg(finalAlbum)}\"")
            }

            if (!finalYear.isNullOrBlank()) {
                metadataArgs.add("-metadata date=\"${escapeForFfmpeg(finalYear)}\"")
            }

            if (!finalGenre.isNullOrBlank()) {
                metadataArgs.add("-metadata genre=\"${escapeForFfmpeg(finalGenre)}\"")
            }

            if (!authorUrl.isNullOrBlank()) {
                metadataArgs.add("-metadata author_url=\"${escapeForFfmpeg(authorUrl)}\"")
            }

            if (!thumbnailUrl.isNullOrBlank()) {
                metadataArgs.add("-metadata thumbnail_url=\"${escapeForFfmpeg(thumbnailUrl)}\"")
            }

            metadataArgs.add("-metadata SB_SKIP_SEGMENTS=\"${escapeForFfmpeg(sbSkipSegments)}\"")
            metadataArgs.add("-metadata SB_VERSION=\"${escapeForFfmpeg(BuildConfig.VERSION_NAME)}\"")
            metadataArgs.add("-metadata SB_PROCESSED=\"true\"")
            metadataArgs.add("-metadata YOUTUBE_VIDEO_URL=\"${escapeForFfmpeg(youtubeUrl)}\"")
            metadataArgs.add("-metadata YOUTUBE_VIDEO_ID=\"${escapeForFfmpeg(videoId)}\"")

            val metadataArgsStr = metadataArgs.joinToString(" ")
            val metadataArgsPart = if (metadataArgsStr.isNotEmpty()) " $metadataArgsStr" else ""

            val command =
                if (thumbFile != null && thumbFile.exists()) {
                    if (extension == "mp3") {
                        "-y -i \"${inputFile.absolutePath}\" -i \"${thumbFile.absolutePath}\" " +
                            "-map 0 -map 1 -c copy -id3v2_version 3$metadataArgsPart " +
                            "-metadata comment=\"${escapeForFfmpeg(
                                metadataJson,
                            )}\" -metadata:s:v title=\"Album cover\" -metadata:s:v comment=\"Cover (front)\" \"${taggedOutput.absolutePath}\""
                    } else { // m4a
                        "-y -i \"${inputFile.absolutePath}\" -i \"${thumbFile.absolutePath}\" " +
                            "-map 0 -map 1 -c copy -disposition:v:0 attached_pic$metadataArgsPart " +
                            "-metadata comment=\"${escapeForFfmpeg(
                                metadataJson,
                            )}\" \"${taggedOutput.absolutePath}\""
                    }
                } else {
                    "-y -i \"${inputFile.absolutePath}\"$metadataArgsPart -metadata comment=\"${escapeForFfmpeg(
                        metadataJson,
                    )}\" -codec copy \"${taggedOutput.absolutePath}\""
                }

            val session =
                try {
                    FFmpegKit.execute(command)
                } finally {
                    thumbFile?.let { if (it.exists()) it.delete() }
                }
            val returnCode = session.returnCode

            return if (ReturnCode.isSuccess(returnCode) && taggedOutput.exists()) {
                inputFile.delete()
                taggedOutput
            } else {
                AppLogger.worker("Metadata tagging skipped or failed for ${inputFile.name}: ${session.allLogsAsString}")
                taggedOutput.delete()
                inputFile
            }
        }

        internal fun buildProcessedMetadataJson(
            videoId: String,
            youtubeUrl: String,
            youtubeTitle: String,
            authorName: String?,
            authorUrl: String?,
            thumbnailUrl: String?,
            categories: List<String>,
            sbSkipSegments: String,
        ): String {
            val escapedTitle = youtubeTitle.replace("\\", "\\\\").replace("\"", "\\\"")
            val escapedUrl = youtubeUrl.replace("\\", "\\\\").replace("\"", "\\\"")
            val escapedVideoId = videoId.replace("\\", "\\\\").replace("\"", "\\\"")
            val escapedAuthorName = authorName?.replace("\\", "\\\\")?.replace("\"", "\\\"").orEmpty()
            val escapedAuthorUrl = authorUrl?.replace("\\", "\\\\")?.replace("\"", "\\\"").orEmpty()
            val escapedThumbnailUrl = thumbnailUrl?.replace("\\", "\\\\")?.replace("\"", "\\\"").orEmpty()
            val removedCategories = categories.joinToString(",") { "\"${it.replace("\\", "\\\\").replace("\"", "\\\"")}\"" }
            return "{" +
                "\"processed\":true," +
                "\"youtubeId\":\"$escapedVideoId\"," +
                "\"youtubeUrl\":\"$escapedUrl\"," +
                "\"youtubeTitle\":\"$escapedTitle\"," +
                "\"authorName\":\"$escapedAuthorName\"," +
                "\"authorUrl\":\"$escapedAuthorUrl\"," +
                "\"thumbnailUrl\":\"$escapedThumbnailUrl\"," +
                "\"processedAt\":\"${System.currentTimeMillis()}\"," +
                "\"sbskipVersion\":\"${BuildConfig.VERSION_NAME}\"," +
                "\"removedCategories\":[$removedCategories]," +
                "\"sbSkipSegments\":\"${sbSkipSegments.replace("\\", "\\\\").replace("\"", "\\\"")}\"" +
                "}"
        }

        private data class ExistingMetadata(
            val title: String?,
            val artist: String?,
            val album: String?,
            val year: String?,
            val genre: String?,
        )

        private fun getExistingMetadata(file: File): ExistingMetadata {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(file.absolutePath)
                val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
                val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                val author = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR)
                val authorName = artist?.takeIf { it.isNotBlank() } ?: author?.takeIf { it.isNotBlank() }
                val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() }
                val year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.takeIf { it.isNotBlank() }
                val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)?.takeIf { it.isNotBlank() }

                ExistingMetadata(title, authorName, album, year, genre)
            } catch (e: Exception) {
                AppLogger.error("MediaTagger", e, "Failed to read metadata for ${file.name}")
                ExistingMetadata(null, null, null, null, null)
            } finally {
                try {
                    retriever.release()
                } catch (ignored: Exception) {
                }
            }
        }

        private fun escapeForFfmpeg(value: String): String {
            return value.replace("\\", "\\\\").replace("\"", "\\\"")
        }

        internal fun resolveCategoriesForMetadata(processingContext: ProcessingContext): List<String> {
            val detectedCategories = processingContext.segments?.map { it.category }?.toSet() ?: emptySet()
            return detectedCategories.intersect(processingContext.categories).map { it.name }
        }
    }

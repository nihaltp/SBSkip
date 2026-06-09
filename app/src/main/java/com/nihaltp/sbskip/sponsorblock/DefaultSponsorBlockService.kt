package com.nihaltp.sbskip.sponsorblock

import com.nihaltp.sbskip.data.repository.SettingsRepository
import com.nihaltp.sbskip.model.SponsorBlockCategory
import com.nihaltp.sbskip.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultSponsorBlockService
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) : SponsorBlockService {
        private val client =
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

        @Serializable
        private data class StatusPageAttributes(
            val aggregate_state: String,
        )

        @Serializable
        private data class StatusPageData(
            val attributes: StatusPageAttributes,
        )

        @Serializable
        private data class StatusPageResponse(
            val data: StatusPageData,
        )

        @Serializable
        private data class SponsorBlockResponseSegment(
            val category: String,
            val segment: List<Double>,
            val actionType: String? = null,
            val UUID: String? = null,
        )

        override suspend fun checkApiStatus(): String =
            withContext(Dispatchers.IO) {
                val currentSettings = settingsRepository.settings.first()
                val rawStatusUrl = currentSettings.sponsorBlockStatusUrl.trim()
                val url =
                    if (rawStatusUrl.endsWith(".json", ignoreCase = true)) {
                        rawStatusUrl
                    } else {
                        val baseStatusUrl = rawStatusUrl.trimEnd('/')
                        "$baseStatusUrl/index.json"
                    }
                AppLogger.worker("Checking SponsorBlock API status via: $url")
                val request =
                    Request.Builder()
                        .url(url)
                        .get()
                        .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Status page returned error code ${response.code}: ${response.message}")
                    }
                    val bodyString = response.body?.string() ?: throw IOException("Empty status page response body")
                    val json = Json { ignoreUnknownKeys = true }
                    val statusResponse = json.decodeFromString<StatusPageResponse>(bodyString)
                    statusResponse.data.attributes.aggregate_state
                }
            }

        override suspend fun fetchSegments(
            videoId: String,
            categories: Set<SponsorBlockCategory>,
        ): List<SponsorBlockSegment> =
            withContext(Dispatchers.IO) {
                if (categories.isEmpty()) return@withContext emptyList()

                val currentSettings = settingsRepository.settings.first()
                val baseUrl = currentSettings.sponsorBlockUrl.trimEnd('/')

                val categoriesParam =
                    categories.map { category ->
                        when (category) {
                            SponsorBlockCategory.SPONSOR -> "sponsor"
                            SponsorBlockCategory.SELF_PROMOTION -> "selfpromo"
                            SponsorBlockCategory.INTERACTION_REMINDER -> "interaction"
                            SponsorBlockCategory.INTRO -> "intro"
                            SponsorBlockCategory.OUTRO -> "outro"
                            SponsorBlockCategory.PREVIEW_RECAP -> "preview"
                            SponsorBlockCategory.HOOK -> "exclusiveAccess"
                            SponsorBlockCategory.FILLER_TANGENT -> "filler"
                            SponsorBlockCategory.MUSIC_OFFTOPIC -> "music_offtopic"
                        }
                    }.joinToString(prefix = "[\"", postfix = "\"]", separator = "\",\"")

                val url = "$baseUrl/api/skipSegments?videoID=$videoId&categories=$categoriesParam"
                AppLogger.worker("SponsorBlock request: $url")

                val request =
                    Request.Builder()
                        .url(url)
                        .get()
                        .build()

                client.newCall(request).execute().use { response ->
                    if (response.code == 404) {
                        AppLogger.worker("SponsorBlock segments not found (404) for videoID=$videoId")
                        return@withContext emptyList()
                    }
                    if (!response.isSuccessful) {
                        throw IOException("SponsorBlock API returned error code ${response.code}: ${response.message}")
                    }

                    val bodyString = response.body?.string() ?: throw IOException("Empty response body")
                    AppLogger.worker("SponsorBlock response: $bodyString")

                    val json = Json { ignoreUnknownKeys = true }
                    val apiSegments = json.decodeFromString<List<SponsorBlockResponseSegment>>(bodyString)

                    apiSegments.mapNotNull { apiSegment ->
                        val categoryEnum =
                            when (apiSegment.category) {
                                "sponsor" -> SponsorBlockCategory.SPONSOR
                                "selfpromo" -> SponsorBlockCategory.SELF_PROMOTION
                                "interaction" -> SponsorBlockCategory.INTERACTION_REMINDER
                                "intro" -> SponsorBlockCategory.INTRO
                                "outro" -> SponsorBlockCategory.OUTRO
                                "preview" -> SponsorBlockCategory.PREVIEW_RECAP
                                "exclusiveAccess" -> SponsorBlockCategory.HOOK
                                "filler" -> SponsorBlockCategory.FILLER_TANGENT
                                "music_offtopic" -> SponsorBlockCategory.MUSIC_OFFTOPIC
                                else -> return@mapNotNull null
                            }

                        if (apiSegment.segment.size < 2) return@mapNotNull null

                        SponsorBlockSegment(
                            category = categoryEnum,
                            startSeconds = apiSegment.segment[0],
                            endSeconds = apiSegment.segment[1],
                        )
                    }
                }
            }
    }

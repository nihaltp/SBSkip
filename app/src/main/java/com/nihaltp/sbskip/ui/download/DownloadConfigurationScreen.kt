package com.nihaltp.sbskip.ui.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nihaltp.sbskip.model.DownloadConfigurationState
import com.nihaltp.sbskip.model.MediaType
import com.nihaltp.sbskip.model.SponsorBlockCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadConfigurationScreen(
    state: DownloadConfigurationState,
    onBack: () -> Unit,
    onToggleCategory: (SponsorBlockCategory) -> Unit,
    onMediaTypeChange: (MediaType) -> Unit,
    onUseGlobalSettingsChange: (Boolean) -> Unit,
    onStartDownload: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Download configuration") },
                navigationIcon = {
                    OutlinedButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Back")
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Preview", fontWeight = FontWeight.SemiBold)
                        Text(state.title.ifBlank { "Selected video" })
                        Text(state.thumbnailUrl ?: "Thumbnail will appear after metadata fetch.")
                    }
                }
            }

            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Media type", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FilterChip(selected = state.request.mediaType == MediaType.VIDEO, onClick = { onMediaTypeChange(MediaType.VIDEO) }, label = { Text("Video") })
                            FilterChip(selected = state.request.mediaType == MediaType.AUDIO, onClick = { onMediaTypeChange(MediaType.AUDIO) }, label = { Text("Audio") })
                        }
                        HorizontalDivider()
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Use global SponsorBlock defaults", fontWeight = FontWeight.Medium)
                                Text("Turn this off to override categories for this download.")
                            }
                            Switch(checked = state.request.useGlobalSponsorBlockSettings, onCheckedChange = onUseGlobalSettingsChange)
                        }
                    }
                }
            }

            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("SponsorBlock categories", fontWeight = FontWeight.SemiBold)
                        Text("Enable or disable categories that should be trimmed from the media.")
                        sponsorBlockCategories.forEach { category ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(category.label)
                                Button(onClick = { onToggleCategory(category.category) }) {
                                    Text("Toggle")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Button(onClick = onStartDownload, modifier = Modifier.fillMaxWidth()) {
                    Text("Start download")
                }
            }
        }
    }
}

private data class CategoryRow(val category: SponsorBlockCategory, val label: String)

private val sponsorBlockCategories = listOf(
    CategoryRow(SponsorBlockCategory.SPONSOR, "Sponsor"),
    CategoryRow(SponsorBlockCategory.SELF_PROMOTION, "Self promotion"),
    CategoryRow(SponsorBlockCategory.INTRO, "Intro"),
    CategoryRow(SponsorBlockCategory.OUTRO, "Outro"),
    CategoryRow(SponsorBlockCategory.INTERACTION_REMINDER, "Interaction reminder"),
    CategoryRow(SponsorBlockCategory.PREVIEW_RECAP, "Preview / recap"),
    CategoryRow(SponsorBlockCategory.FILLER_TANGENT, "Filler / tangent"),
)

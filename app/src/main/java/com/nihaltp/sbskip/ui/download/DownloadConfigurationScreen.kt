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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nihaltp.sbskip.R
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
                title = { Text(stringResource(id = R.string.download_config_title)) },
                navigationIcon = {
                    OutlinedButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(id = com.nihaltp.sbskip.R.string.settings_back))
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
                        Text(stringResource(id = R.string.preview), fontWeight = FontWeight.SemiBold)
                        Text(state.title.ifBlank { stringResource(id = R.string.selected_video) })
                        Text(state.thumbnailUrl ?: stringResource(id = R.string.thumbnail_will_appear))
                    }
                }
            }

            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(id = R.string.media_type), fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FilterChip(selected = state.request.mediaType == MediaType.VIDEO, onClick = { onMediaTypeChange(MediaType.VIDEO) }, label = { Text(stringResource(id = R.string.label_video)) })
                            FilterChip(selected = state.request.mediaType == MediaType.AUDIO, onClick = { onMediaTypeChange(MediaType.AUDIO) }, label = { Text(stringResource(id = R.string.label_audio)) })
                        }
                        HorizontalDivider()
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(id = R.string.use_global_sponsorblock_defaults), fontWeight = FontWeight.Medium)
                                Text(stringResource(id = R.string.sponsorblock_override_desc))
                            }
                            Switch(checked = state.request.useGlobalSponsorBlockSettings, onCheckedChange = onUseGlobalSettingsChange)
                        }
                    }
                }
            }

            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(id = R.string.sponsorblock_categories), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(id = R.string.sponsorblock_categories_desc))
                        sponsorBlockCategories.forEach { category ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(id = category.labelResId))
                                Button(onClick = { onToggleCategory(category.category) }) {
                                    Text(stringResource(id = R.string.toggle))
                                }
                            }
                        }
                    }
                }
            }

            item {
                Button(onClick = onStartDownload, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(id = R.string.start_download))
                }
            }
        }
    }
}

private data class CategoryRow(val category: SponsorBlockCategory, val labelResId: Int)

private val sponsorBlockCategories = listOf(
    CategoryRow(SponsorBlockCategory.SPONSOR, com.nihaltp.sbskip.R.string.category_sponsor),
    CategoryRow(SponsorBlockCategory.SELF_PROMOTION, com.nihaltp.sbskip.R.string.category_self_promotion),
    CategoryRow(SponsorBlockCategory.INTRO, com.nihaltp.sbskip.R.string.category_intro),
    CategoryRow(SponsorBlockCategory.OUTRO, com.nihaltp.sbskip.R.string.category_outro),
    CategoryRow(SponsorBlockCategory.INTERACTION_REMINDER, com.nihaltp.sbskip.R.string.category_interaction_reminder),
    CategoryRow(SponsorBlockCategory.PREVIEW_RECAP, com.nihaltp.sbskip.R.string.category_preview_recap),
    CategoryRow(SponsorBlockCategory.FILLER_TANGENT, com.nihaltp.sbskip.R.string.category_filler_tangent),
)

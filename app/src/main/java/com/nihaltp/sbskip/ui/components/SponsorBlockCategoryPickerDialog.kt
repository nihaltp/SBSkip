package com.nihaltp.sbskip.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nihaltp.sbskip.R
import com.nihaltp.sbskip.model.SponsorBlockCategory

data class CategoryRow(val category: SponsorBlockCategory, val labelResId: Int)

val sponsorBlockCategories =
    listOf(
        CategoryRow(SponsorBlockCategory.SPONSOR, R.string.category_sponsor),
        CategoryRow(SponsorBlockCategory.SELF_PROMOTION, R.string.category_self_promotion),
        CategoryRow(SponsorBlockCategory.INTERACTION_REMINDER, R.string.category_interaction_reminder),
        CategoryRow(SponsorBlockCategory.INTRO, R.string.category_intro),
        CategoryRow(SponsorBlockCategory.OUTRO, R.string.category_outro),
        CategoryRow(SponsorBlockCategory.PREVIEW_RECAP, R.string.category_preview_recap),
        CategoryRow(SponsorBlockCategory.HOOK, R.string.category_hook_greetings),
        CategoryRow(SponsorBlockCategory.FILLER_TANGENT, R.string.category_filler_tangent),
        CategoryRow(SponsorBlockCategory.MUSIC_OFFTOPIC, R.string.category_music_non_music_section),
    )

@Composable
fun SponsorBlockCategoryPickerDialog(
    title: String,
    initialCategories: Set<SponsorBlockCategory>,
    onCategoriesChanged: (Set<SponsorBlockCategory>) -> Unit,
    onDismissRequest: () -> Unit,
    showResetButton: Boolean = false,
    onReset: (() -> Unit)? = null,
) {
    var tempCategories by remember(initialCategories) { mutableStateOf(initialCategories) }
    val allSelected = tempCategories.size == sponsorBlockCategories.size

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(350.dp).fillMaxWidth(),
            ) {
                item {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    tempCategories =
                                        if (allSelected) {
                                            emptySet()
                                        } else {
                                            SponsorBlockCategory.entries.toSet()
                                        }
                                }
                                .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(id = R.string.select_all), fontWeight = FontWeight.Bold)
                        Checkbox(
                            checked = allSelected,
                            onCheckedChange = {
                                tempCategories =
                                    if (allSelected) {
                                        emptySet()
                                    } else {
                                        SponsorBlockCategory.entries.toSet()
                                    }
                            },
                        )
                    }
                    HorizontalDivider()
                }

                sponsorBlockCategories.forEach { categoryRow ->
                    item {
                        val isChecked = categoryRow.category in tempCategories
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        tempCategories =
                                            if (isChecked) {
                                                tempCategories - categoryRow.category
                                            } else {
                                                tempCategories + categoryRow.category
                                            }
                                    }
                                    .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(id = categoryRow.labelResId))
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = {
                                    tempCategories =
                                        if (isChecked) {
                                            tempCategories - categoryRow.category
                                        } else {
                                            tempCategories + categoryRow.category
                                        }
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onCategoriesChanged(tempCategories)
                onDismissRequest()
            }) {
                Text(stringResource(id = R.string.done))
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showResetButton && onReset != null) {
                    TextButton(onClick = {
                        onReset()
                        onDismissRequest()
                    }) {
                        Text(stringResource(id = R.string.categories_dialog_reset))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        },
    )
}

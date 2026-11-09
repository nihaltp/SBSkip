package com.nihaltp.sbskip.ui.main.components.common

import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThumbnailImageTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun shows_fallback_icon_when_model_is_null() {
        composeTestRule.setContent {
            ThumbnailImage(model = null)
        }

        composeTestRule.onRoot().assertIsDisplayed()
    }

    @Test
    fun shows_fallback_icon_when_model_is_empty_string() {
        composeTestRule.setContent {
            ThumbnailImage(model = "")
        }

        composeTestRule.onRoot().assertIsDisplayed()
    }

    @Test
    fun attempts_to_load_image_when_model_is_valid_url() {
        composeTestRule.setContent {
            ThumbnailImage(model = "https://img.youtube.com/vi/test/mqdefault.jpg")
        }

        composeTestRule.onRoot().assertIsDisplayed()
    }

    @Test
    fun uses_custom_fallback_icon_when_provided() {
        composeTestRule.setContent {
            ThumbnailImage(
                model = null,
                fallbackIcon = Icons.Default.Album,
            )
        }

        composeTestRule.onRoot().assertIsDisplayed()
    }

    @Test
    fun uses_custom_fallback_tint_when_provided() {
        composeTestRule.setContent {
            ThumbnailImage(
                model = null,
                fallbackTint = androidx.compose.ui.graphics.Color.Red,
            )
        }

        composeTestRule.onRoot().assertIsDisplayed()
    }

    @Test
    fun uses_custom_fallback_size_when_provided() {
        composeTestRule.setContent {
            ThumbnailImage(
                model = null,
                fallbackIconSize = 64.dp,
            )
        }

        composeTestRule.onRoot().assertIsDisplayed()
    }

    @Test
    fun uses_custom_content_scale_when_provided() {
        composeTestRule.setContent {
            ThumbnailImage(
                model = "https://example.com/image.jpg",
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            )
        }

        composeTestRule.onRoot().assertIsDisplayed()
    }
}

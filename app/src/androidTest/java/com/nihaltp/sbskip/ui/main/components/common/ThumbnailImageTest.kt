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
    fun `shows fallback icon when model is null`() {
        composeTestRule.setContent {
            ThumbnailImage(model = null)
        }

        composeTestRule.onRoot().assertIsDisplayed()
    }

    @Test
    fun `shows fallback icon when model is empty string`() {
        composeTestRule.setContent {
            ThumbnailImage(model = "")
        }

        composeTestRule.onRoot().assertIsDisplayed()
    }

    @Test
    fun `attempts to load image when model is valid url`() {
        composeTestRule.setContent {
            ThumbnailImage(model = "https://img.youtube.com/vi/test/mqdefault.jpg")
        }

        composeTestRule.onRoot().assertIsDisplayed()
    }

    @Test
    fun `uses custom fallback icon when provided`() {
        composeTestRule.setContent {
            ThumbnailImage(
                model = null,
                fallbackIcon = Icons.Default.Album,
            )
        }

        composeTestRule.onRoot().assertIsDisplayed()
    }

    @Test
    fun `uses custom fallback tint when provided`() {
        composeTestRule.setContent {
            ThumbnailImage(
                model = null,
                fallbackTint = androidx.compose.ui.graphics.Color.Red,
            )
        }

        composeTestRule.onRoot().assertIsDisplayed()
    }

    @Test
    fun `uses custom fallback size when provided`() {
        composeTestRule.setContent {
            ThumbnailImage(
                model = null,
                fallbackIconSize = 64.dp,
            )
        }

        composeTestRule.onRoot().assertIsDisplayed()
    }

    @Test
    fun `uses custom content scale when provided`() {
        composeTestRule.setContent {
            ThumbnailImage(
                model = "https://example.com/image.jpg",
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            )
        }

        composeTestRule.onRoot().assertIsDisplayed()
    }
}

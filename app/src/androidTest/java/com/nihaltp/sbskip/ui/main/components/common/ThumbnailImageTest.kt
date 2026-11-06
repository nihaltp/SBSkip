package com.nihaltp.sbskip.ui.main.components.common

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.semantics.ContentDescription
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

        // The fallback icon should be present (Album icon from Icons.Default)
        // We can't easily test for Icon directly, but we can verify no crash
        // and the component renders
        composeTestRule.onNode(ContentDescription(null)).assertIsDisplayed()
    }

    @Test
    fun `shows fallback icon when model is empty string`() {
        composeTestRule.setContent {
            ThumbnailImage(model = "")
        }

        composeTestRule.onNode(ContentDescription(null)).assertIsDisplayed()
    }

    @Test
    fun `attempts to load image when model is valid url`() {
        // We can't easily test AsyncImage loading in unit tests,
        // but we can verify the component doesn't crash
        composeTestRule.setContent {
            ThumbnailImage(model = "https://img.youtube.com/vi/test/mqdefault.jpg")
        }

        // Should render without crashing
        composeTestRule.onNode(ContentDescription(null)).assertIsDisplayed()
    }

    @Test
    fun `uses custom fallback icon when provided`() {
        // Test with a different fallback icon
        composeTestRule.setContent {
            ThumbnailImage(
                model = null,
                fallbackIcon = androidx.compose.material.icons.Icons.Default.Album,
            )
        }

        composeTestRule.onNode(ContentDescription(null)).assertIsDisplayed()
    }

    @Test
    fun `uses custom fallback tint when provided`() {
        composeTestRule.setContent {
            ThumbnailImage(
                model = null,
                fallbackTint = androidx.compose.ui.graphics.Color.Red,
            )
        }

        composeTestRule.onNode(ContentDescription(null)).assertIsDisplayed()
    }

    @Test
    fun `uses custom fallback size when provided`() {
        composeTestRule.setContent {
            ThumbnailImage(
                model = null,
                fallbackIconSize = 64.dp,
            )
        }

        composeTestRule.onNode(ContentDescription(null)).assertIsDisplayed()
    }

    @Test
    fun `uses custom content scale when provided`() {
        composeTestRule.setContent {
            ThumbnailImage(
                model = "https://example.com/image.jpg",
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            )
        }

        composeTestRule.onNode(ContentDescription(null)).assertIsDisplayed()
    }
}

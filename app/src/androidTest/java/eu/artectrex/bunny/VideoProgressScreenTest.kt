package eu.artectrex.bunny

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import eu.artectrex.bunny.start.StartTags
import eu.artectrex.bunny.start.VideoProgressContent
import org.pixeldroid.media_editor.common.ui.BunnyTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The video progress screen on its own */
@RunWith(AndroidJUnit4::class)
class VideoProgressScreenTest {

    @get:Rule val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun runningConversionShowsProgressAndCanBeCancelled() {
        var cancelled = false
        compose.setContent {
            BunnyTheme { VideoProgressContent(VideoProgress(VideoPhase.ENCODING, 42), onBack = {}, onCancel = { cancelled = true }) }
        }
        compose.onNodeWithText(context.getString(R.string.video_encoding, 42)).assertIsDisplayed()
        compose.onNodeWithTag(StartTags.VIDEO_PROGRESS).assertIsDisplayed()
        // Regression: the cancel button was not connected to anything
        compose.onNodeWithTag(StartTags.CANCEL_VIDEO).assertIsDisplayed().performClick()
        assertTrue("cancel was called", cancelled)
    }

    @Test fun finishedConversionHasNoCancelButton() {
        compose.setContent {
            BunnyTheme { VideoProgressContent(VideoProgress(VideoPhase.FINISHED, 100), onBack = {}, onCancel = {}) }
        }
        compose.onNodeWithText(context.getString(R.string.video_finished)).assertIsDisplayed()
        compose.onNodeWithTag(StartTags.CANCEL_VIDEO).assertDoesNotExist()
        compose.onNodeWithTag(StartTags.VIDEO_PROGRESS).assertDoesNotExist()
    }
}

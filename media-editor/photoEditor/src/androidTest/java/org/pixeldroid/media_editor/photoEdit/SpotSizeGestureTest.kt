package org.pixeldroid.media_editor.photoEdit

import android.content.Intent
import android.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.pixeldroid.media_editor.common.PICTURE_URI
import org.pixeldroid.media_editor.photoEdit.imagine.layers.SwirlLayer

/** Two fingers on the photo change the size of the filter's area, not its centre */
@RunWith(AndroidJUnit4::class)
class SpotSizeGestureTest {

    @get:Rule val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun spreadingTwoFingersMakesTheAreaLarger() {
        val uri = TestImages.write(context, "spot.png", TestImages.solid(800, 800, Color.GRAY))
        ActivityScenario.launch<PhotoEditActivity>(Intent(context, PhotoEditActivity::class.java).putExtra(PICTURE_URI, uri)).use { scenario ->
            scenario.awaitImageLoaded()
            lateinit var model: PhotoEditViewModel
            scenario.onActivity { activity ->
                model = ViewModelProvider(activity, PhotoEditViewModelFactory())[PhotoEditViewModel::class.java]
                model.doChange(PhotoEditViewModel.Change.SelectFilter(SwirlLayer()))
                // The centre away from where the fingers come down
                model.onFilterCenterChange(0.2f, 0.3f)
            }
            compose.waitForIdle()
            compose.onNodeWithTag(EditorTags.FILTER_CENTER).performTouchInput {
                // Spread from close together to far apart, around the middle
                pinch(
                    start0 = center - Offset(20f, 0f), end0 = center - Offset(width / 3f, 0f),
                    start1 = center + Offset(20f, 0f), end1 = center + Offset(width / 3f, 0f),
                )
            }
            compose.waitForIdle()
            val settings = model.filter.value!!.settings
            assertTrue("larger: ${settings.size}", settings.size > 1.5f)
            // Regression: the centre jumped to where the first finger came down
            assertEquals("centre not moved", 0.2f, settings.centerX, 0.001f)
            assertEquals("centre not moved", 0.3f, settings.centerY, 0.001f)
        }
    }

    /** One finger still moves the centre: tapping, and dragging */
    @Test fun oneFingerMovesTheCentre() {
        val uri = TestImages.write(context, "spot-move.png", TestImages.solid(800, 800, Color.GRAY))
        ActivityScenario.launch<PhotoEditActivity>(Intent(context, PhotoEditActivity::class.java).putExtra(PICTURE_URI, uri)).use { scenario ->
            scenario.awaitImageLoaded()
            lateinit var model: PhotoEditViewModel
            scenario.onActivity { activity ->
                model = ViewModelProvider(activity, PhotoEditViewModelFactory())[PhotoEditViewModel::class.java]
                model.doChange(PhotoEditViewModel.Change.SelectFilter(SwirlLayer()))
            }
            compose.waitForIdle()
            compose.onNodeWithTag(EditorTags.FILTER_CENTER).performTouchInput { click(Offset(width * 0.25f, height * 0.75f)) }
            compose.waitForIdle()
            model.filter.value!!.settings.let {
                assertEquals("tapped: x", 0.25f, it.centerX, 0.02f)
                assertEquals("tapped: y", 0.75f, it.centerY, 0.02f)
            }
            compose.onNodeWithTag(EditorTags.FILTER_CENTER).performTouchInput {
                swipe(Offset(width * 0.25f, height * 0.75f), Offset(width * 0.8f, height * 0.2f))
            }
            compose.waitForIdle()
            model.filter.value!!.settings.let {
                assertEquals("dragged: x", 0.8f, it.centerX, 0.02f)
                assertEquals("dragged: y", 0.2f, it.centerY, 0.02f)
            }
        }
    }
}

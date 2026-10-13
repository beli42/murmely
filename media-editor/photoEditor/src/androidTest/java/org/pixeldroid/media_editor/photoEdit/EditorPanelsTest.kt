package org.pixeldroid.media_editor.photoEdit

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import org.pixeldroid.media_editor.common.ui.BunnyTheme
import org.pixeldroid.media_editor.photoEdit.panel.AdjustmentPanel
import org.pixeldroid.media_editor.photoEdit.panel.HistoryControls
import org.pixeldroid.media_editor.photoEdit.panel.PanelTags
import org.pixeldroid.media_editor.photoEdit.panel.ToolsPanel

/** The Compose panels of the photo editor on their own */
@RunWith(AndroidJUnit4::class)
class EditorPanelsTest {

    @get:Rule val compose = createComposeRule()

    @Test fun everySliderIsShownWithItsValue() {
        val sliders = Sliders().with(Adjustment.CONTRAST, 0.45f).with(Adjustment.GRAIN, 0.5f)
        compose.setContent { BunnyTheme { AdjustmentPanel(sliders, onChange = { _, _ -> }) } }
        for (adjustment in Adjustment.entries) {
            compose.onNodeWithTag(PanelTags.slider(adjustment)).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithTag(PanelTags.value(Adjustment.CONTRAST)).assertTextEquals("+50")   // 0.45 of 0.9
        compose.onNodeWithTag(PanelTags.value(Adjustment.GRAIN)).assertTextEquals("+50")
        compose.onNodeWithTag(PanelTags.value(Adjustment.EXPOSURE)).assertTextEquals("0")
    }

    @Test fun tappingTheValueResetsTheSlider() {
        var changed: Pair<Adjustment, Float>? = null
        compose.setContent {
            BunnyTheme {
                AdjustmentPanel(Sliders().with(Adjustment.WARMTH, 0.7f), onChange = { a, v -> changed = a to v })
            }
        }
        compose.onNodeWithTag(PanelTags.value(Adjustment.WARMTH)).performScrollTo().performClick()
        assertEquals(Adjustment.WARMTH to 0f, changed)
    }

    @Test fun historyButtonsFollowWhatCanBeUndone() {
        val clicked = mutableListOf<String>()
        compose.setContent {
            BunnyTheme {
                HistoryControls(canUndo = true, canRedo = false,
                    onUndo = { clicked += "undo" }, onRedo = { clicked += "redo" }, onReset = { clicked += "reset" })
            }
        }
        compose.onNodeWithTag(PanelTags.UNDO).assertIsEnabled().performClick()
        compose.onNodeWithTag(PanelTags.REDO).assertIsNotEnabled()
        compose.onNodeWithTag(PanelTags.RESET).assertIsEnabled().performClick()
        assertEquals(listOf("undo", "reset"), clicked)
    }

    @Test fun toolsAreOffered() {
        val clicked = mutableListOf<String>()
        compose.setContent {
            BunnyTheme {
                ToolsPanel(onDraw = { clicked += "draw" }, onText = { clicked += "text" },
                    onStickers = { clicked += "stickers" }, onCrop = { clicked += "crop" })
            }
        }
        for (tag in listOf(PanelTags.DRAW, PanelTags.TEXT, PanelTags.STICKERS, PanelTags.CROP)) {
            compose.onNodeWithTag(tag).assertIsDisplayed().performClick()
        }
        assertEquals(listOf("draw", "text", "stickers", "crop"), clicked)
    }
}

/** The undo/redo/reset buttons on top of the photo, in the real editor */
@RunWith(AndroidJUnit4::class)
class PhotoEditHistoryButtonsTest {

    @get:Rule val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun undoRedoAndResetOnThePhoto() {
        val uri = TestImages.write(context, "history-source.png", TestImages.gradient(800, 600))
        ActivityScenario.launch<PhotoEditActivity>(
            Intent(context, PhotoEditActivity::class.java).putExtra(PICTURE_URI, uri)
        ).use { scenario ->
            lateinit var model: PhotoEditViewModel
            scenario.onActivity { model = ViewModelProvider(it, PhotoEditViewModelFactory())[PhotoEditViewModel::class.java] }

            // Nothing to undo yet
            compose.onNodeWithTag(PanelTags.UNDO).assertIsDisplayed().assertIsNotEnabled()
            compose.onNodeWithTag(PanelTags.REDO).assertIsNotEnabled()

            // A slider change is recorded after it stopped for a moment
            scenario.onActivity { model.onAdjustmentChange(Adjustment.CONTRAST, 0.5f) }
            compose.waitUntil(5_000) { model.canUndo.value }
            compose.onNodeWithTag(PanelTags.UNDO).assertIsEnabled().performClick()
            assertEquals(Sliders(), model.sliders.value)
            compose.onNodeWithTag(PanelTags.REDO).assertIsEnabled().performClick()
            assertEquals(0.5f, model.sliders.value[Adjustment.CONTRAST], 0f)

            // Reset undoes everything, and can be redone
            compose.onNodeWithTag(PanelTags.RESET).performClick()
            assertTrue(model.sliders.value.isNeutral)
            compose.onNodeWithTag(PanelTags.REDO).assertIsEnabled()
        }
    }
}

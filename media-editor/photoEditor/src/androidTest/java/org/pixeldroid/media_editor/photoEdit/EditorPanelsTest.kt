package org.pixeldroid.media_editor.photoEdit

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import org.junit.Test
import org.junit.runner.RunWith
import org.pixeldroid.media_editor.common.PICTURE_URI
import org.pixeldroid.media_editor.common.ui.BunnyTheme
import org.pixeldroid.media_editor.photoEdit.panel.AdjustmentPanel
import org.pixeldroid.media_editor.photoEdit.panel.HistoryControls
import org.pixeldroid.media_editor.photoEdit.panel.PanelTags
import org.pixeldroid.media_editor.photoEdit.panel.ToolSettingsPanel
import org.pixeldroid.media_editor.photoEdit.panel.ToolsPanel
import org.pixeldroid.media_editor.photoEdit.overlay.TextFont
import org.pixeldroid.media_editor.photoEdit.overlay.ToolStyle

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

    @Test fun toolSettingsForDrawingAndText() {
        var style = ToolStyle()
        var forText by mutableStateOf(false)
        compose.setContent { BunnyTheme { ToolSettingsPanel(style, forText, onChange = { style = it }) } }
        for (tag in listOf(PanelTags.HUE, PanelTags.LIGHTNESS, PanelTags.SIZE, PanelTags.STYLE_PREVIEW)) {
            compose.onNodeWithTag(tag).assertIsDisplayed()
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithText(context.getString(R.string.tool_line_width)).assertIsDisplayed()
        forText = true
        // Texts: font instead of size (resized on the photo)
        compose.onNodeWithTag(PanelTags.SIZE).assertDoesNotExist()
        compose.onNodeWithTag(PanelTags.font(TextFont.LOBSTER)).assertExists()
        compose.onNodeWithText("Demo").assertIsDisplayed()
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

    @Test fun toolsAreTogglesWithTheirSettingsBelow() {
        var active by mutableStateOf(PhotoEditViewModel.ShownView.Main)
        var cropped = false
        compose.setContent {
            BunnyTheme {
                ToolsPanel(
                    activeTool = active, style = ToolStyle(),
                    onToggleTool = { active = if (active == it) PhotoEditViewModel.ShownView.Main else it },
                    onCrop = { cropped = true }, onStyleChange = {},
                )
            }
        }
        // No tool: no settings
        compose.onNodeWithTag(PanelTags.HUE).assertDoesNotExist()

        compose.onNodeWithTag(PanelTags.DRAW).performClick()
        assertEquals(PhotoEditViewModel.ShownView.Draw, active)
        compose.onNodeWithTag(PanelTags.DRAW).assertIsSelected()
        compose.onNodeWithTag(PanelTags.HUE).assertIsDisplayed()

        compose.onNodeWithTag(PanelTags.TEXT).performClick()
        assertEquals(PhotoEditViewModel.ShownView.Text, active)
        compose.onNodeWithTag(PanelTags.DRAW).assertIsNotSelected()

        // Tapping the active tool again finishes it
        compose.onNodeWithTag(PanelTags.TEXT).performClick()
        assertEquals(PhotoEditViewModel.ShownView.Main, active)
        compose.onNodeWithTag(PanelTags.HUE).assertDoesNotExist()

        compose.onNodeWithTag(PanelTags.CROP).performClick()
        assertTrue(cropped)
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

    /** Sliders and filters can be used at any time: leaving the Edit tab finishes the tool */
    @Test fun switchingTabsFinishesTheTool() {
        val uri = TestImages.write(context, "tabs-source.png", TestImages.gradient(800, 600))
        ActivityScenario.launch<PhotoEditActivity>(
            Intent(context, PhotoEditActivity::class.java).putExtra(PICTURE_URI, uri)
        ).use { scenario ->
            lateinit var model: PhotoEditViewModel
            scenario.onActivity {
                model = ViewModelProvider(it, PhotoEditViewModelFactory())[PhotoEditViewModel::class.java]
            }
            compose.onNodeWithTag(EditorTags.tab(EditorTab.EDIT)).performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithTag(PanelTags.DRAW).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag(PanelTags.DRAW).performClick()
            assertEquals(PhotoEditViewModel.ShownView.Draw, model.shownView.value)
            // Tabs are still there while drawing
            compose.onNodeWithTag(EditorTags.tab(EditorTab.SLIDERS)).assertIsDisplayed().performClick()
            compose.waitForIdle()
            assertEquals(PhotoEditViewModel.ShownView.Main, model.shownView.value)
        }
    }
}

/** The undo controls on the photo: tapping the handle moves them to the next corner, clockwise */
@RunWith(AndroidJUnit4::class)
class MovableHistoryControlsTest {

    @get:Rule val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun handleMovesThemRoundTheCorners() {
        val uri = TestImages.write(context, "controls-source.png", TestImages.gradient(800, 600))
        ActivityScenario.launch<PhotoEditActivity>(
            Intent(context, PhotoEditActivity::class.java).putExtra(PICTURE_URI, uri)
        ).use { scenario ->
            scenario.awaitImageLoaded()
            fun undo() = compose.onNodeWithTag(PanelTags.UNDO).getUnclippedBoundsInRoot()
            val topRight = undo()
            compose.onNodeWithTag(PanelTags.HISTORY_HANDLE).performClick()
            compose.waitForIdle()
            val bottomRight = undo()
            assertTrue("down on the right", bottomRight.top > topRight.top && bottomRight.left == topRight.left)
            compose.onNodeWithTag(PanelTags.HISTORY_HANDLE).performClick()
            compose.waitForIdle()
            val bottomLeft = undo()
            assertTrue("then to the left", bottomLeft.left < bottomRight.left && bottomLeft.top == bottomRight.top)
            compose.onNodeWithTag(PanelTags.HISTORY_HANDLE).performClick()
            compose.waitForIdle()
            val topLeft = undo()
            assertTrue("then up", topLeft.top < bottomLeft.top && topLeft.left == bottomLeft.left)
            compose.onNodeWithTag(PanelTags.HISTORY_HANDLE).performClick()
            compose.waitForIdle()
            assertEquals("round again: top right", topRight, undo())
        }
    }
}

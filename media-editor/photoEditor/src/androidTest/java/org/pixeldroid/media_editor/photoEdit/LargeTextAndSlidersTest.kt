package org.pixeldroid.media_editor.photoEdit

import android.content.Intent
import android.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.pixeldroid.media_editor.common.PICTURE_URI
import org.pixeldroid.media_editor.common.ui.BunnyTheme
import org.pixeldroid.media_editor.photoEdit.imagine.layers.ElsaLayer
import org.pixeldroid.media_editor.photoEdit.panel.AdjustmentPanel
import org.pixeldroid.media_editor.photoEdit.panel.FilterCategory
import org.pixeldroid.media_editor.photoEdit.panel.FilterPanel
import org.pixeldroid.media_editor.photoEdit.panel.FilterTags
import org.pixeldroid.media_editor.photoEdit.panel.PanelTags

/** Large system text, resetting sliders, and the photo area's height */
@RunWith(AndroidJUnit4::class)
class LargeTextAndSlidersTest {

    @get:Rule val compose = createComposeRule()

    /** Like a phone with the system text at 200 % */
    private fun largeText(content: @androidx.compose.runtime.Composable () -> Unit) = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
            BunnyTheme { content() }
        }
    }

    private fun lines(tag: String): Int {
        val results = mutableListOf<TextLayoutResult>()
        // The tab merges its children: the name is in the unmerged tree
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single().lineCount
    }

    @Test fun tabNamesStayOnOneLineWithLargeText() {
        largeText { EditorTabRow(EditorTab.FILTERS) {} }
        EditorTab.entries.forEach { tab -> assertEquals("${tab.name} on one line", 1, lines(EditorTags.tabLabel(tab))) }
    }

    @Test fun applyCoversNoGroupWithLargeText() {
        largeText {
            FilterPanel(
                groups = mapOf(FilterCategory.COLOR to listOf(ElsaLayer())), thumbnailOf = { null },
                selected = ElsaLayer(), vertical = false,
                onSelect = {}, onApply = {}, onLongPress = {}, onCreate = {},
            )
        }
        val apply = compose.onNodeWithTag(FilterTags.APPLY).getUnclippedBoundsInRoot()
        FilterCategory.entries.forEach { group ->
            // What is visible of the group (it may scroll sideways)
            val chip = compose.onNodeWithTag(FilterTags.category(group)).getBoundsInRoot()
            if (chip.width.value <= 0f) return@forEach
            val overlaps = apply.left < chip.right && chip.left < apply.right && apply.top < chip.bottom && chip.top < apply.bottom
            assertTrue("apply doesn't cover ${group.name}", !overlaps)
            assertTrue("apply in the same line as ${group.name}", apply.top < chip.bottom && chip.top < apply.bottom)
        }
    }

    /** With room, the filter tiles grow beyond the old 64 dp (up to 160) */
    @Test fun filterTilesUseTheRoom() {
        compose.setContent {
            BunnyTheme {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.requiredHeight(320.dp)) {
                    FilterPanel(
                        groups = mapOf(FilterCategory.COLOR to listOf(ElsaLayer())), thumbnailOf = { null },
                        selected = null, vertical = false,
                        onSelect = {}, onApply = {}, onLongPress = {}, onCreate = {},
                    )
                }
            }
        }
        val tile = compose.onNodeWithTag(FilterTags.item(1)).getUnclippedBoundsInRoot()
        assertTrue("tile wider than before: ${tile.width}", tile.width.value > 100f)
        assertTrue("not beyond the limit: ${tile.width}", tile.width.value <= 168f + 0.5f)
        // Apply next to the groups, above the tiles
        val apply = compose.onNodeWithTag(FilterTags.APPLY).getUnclippedBoundsInRoot()
        assertTrue("apply above the tiles", apply.bottom <= tile.top)
    }

    /** Value at 0.5 of -1..1: the handle is at 75 % of the track */
    private fun slidersAt(value: Float, onChange: (Float) -> Unit) {
        var sliders by mutableStateOf(Sliders().with(Adjustment.CONTRAST, value))
        compose.setContent {
            BunnyTheme {
                AdjustmentPanel(sliders, onChange = { a, v -> if (a == Adjustment.CONTRAST) { sliders = sliders.with(a, v); onChange(v) } })
            }
        }
    }

    @Test fun doubleTapOnTheHandleResets() {
        var last = Float.NaN
        slidersAt(0.5f) { last = it }
        compose.onNodeWithTag(PanelTags.slider(Adjustment.CONTRAST)).performTouchInput {
            // The handle's centre: half a handle in, plus 75 % of the rest
            val handle = 28.dp.toPx()
            doubleClick(Offset(handle / 2 + 0.75f * (width - handle), height / 2f))
        }
        // The reset comes a frame after the finger lifts
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertEquals("reset", 0f, last, 0f)
    }

    @Test fun tapOnTheTrackOnlyMoves() {
        var last = Float.NaN
        slidersAt(0.5f) { last = it }
        compose.onNodeWithTag(PanelTags.slider(Adjustment.CONTRAST)).performTouchInput {
            // Far from the handle, near the start: a jump there, twice is no reset
            doubleClick(Offset(width * 0.1f, height / 2f))
        }
        compose.waitForIdle()
        assertTrue("moved towards the start: $last", last < -0.5f)
        assertNotEquals(0f, last, 0f)
    }

    @Test fun tapOnNameOrValueResets() {
        var last = Float.NaN
        slidersAt(0.5f) { last = it }
        compose.onNodeWithTag(PanelTags.name(Adjustment.CONTRAST)).performClick()
        assertEquals(0f, last, 0f)
        last = Float.NaN
        compose.onNodeWithTag(PanelTags.slider(Adjustment.CONTRAST)).performTouchInput { click(Offset(width * 0.9f, height / 2f)) }
        compose.onNodeWithTag(PanelTags.value(Adjustment.CONTRAST)).performClick()
        assertEquals(0f, last, 0f)
    }
}

/** The photo area takes the height the photo needs, the tools the rest */
@RunWith(AndroidJUnit4::class)
class PreviewHeightTest {

    @get:Rule val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun previewHeightFor(width: Int, height: Int): Float {
        val uri = TestImages.write(context, "preview-$width-$height.png", TestImages.solid(width, height, Color.GRAY))
        ActivityScenario.launch<PhotoEditActivity>(Intent(context, PhotoEditActivity::class.java).putExtra(PICTURE_URI, uri)).use { scenario ->
            scenario.awaitImageLoaded()
            compose.waitForIdle()
            return compose.onNodeWithTag(EditorTags.PREVIEW_AREA).getUnclippedBoundsInRoot().height.value
        }
    }

    @Test fun photoGetsAtLeastHalfTheScreenAndTallOnesMore() {
        val wide = previewHeightFor(1600, 900)
        val square = previewHeightFor(1000, 1000)
        val tall = previewHeightFor(900, 1600)
        val screen = compose.onRoot().getUnclippedBoundsInRoot().height.value
        assertTrue("wide $wide <= square $square <= tall $tall", wide <= square + 0.5f && square <= tall + 0.5f)
        // At least 55 % of the editor's height (the screen less the top bar): more than half the screen
        assertTrue("wide photo still gets more than half the screen: $wide of $screen", wide > screen * 0.5f)
        assertTrue("tall photo leaves room for the tools: $tall of $screen", tall < screen * 0.7f)
    }
}

/** In the real editor with the system text at 200 %: the filter tiles show whole */
@RunWith(AndroidJUnit4::class)
class LargeTextEditorTest {

    @get:Rule val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun fontScale(scale: Float) {
        instrumentation.uiAutomation.executeShellCommand("settings put system font_scale $scale").close()
        Thread.sleep(500)
    }

    @Test fun filterTilesShowWholeWithLargeText() {
        fontScale(2f)
        try {
            val uri = TestImages.write(context, "large-text.png", TestImages.solid(1600, 1000, Color.GRAY))
            ActivityScenario.launch<PhotoEditActivity>(Intent(context, PhotoEditActivity::class.java).putExtra(PICTURE_URI, uri)).use { scenario ->
                scenario.awaitImageLoaded()
                compose.waitForIdle()
                val tile = compose.onNodeWithTag(FilterTags.item(1)).getBoundsInRoot()
                val unclipped = compose.onNodeWithTag(FilterTags.item(1)).getUnclippedBoundsInRoot()
                assertEquals("tile not cut off at the bottom", unclipped.height.value, tile.height.value, 1f)
                assertTrue("tile has its square: ${tile.height}", tile.height.value >= 40f)
                val apply = compose.onNodeWithTag(FilterTags.APPLY).getBoundsInRoot()
                val colour = compose.onNodeWithTag(FilterTags.category(FilterCategory.COLOR)).getBoundsInRoot()
                assertTrue("apply in the line of the groups", apply.top < colour.bottom && colour.top < apply.bottom)
            }
        } finally {
            fontScale(1f)
        }
    }
}

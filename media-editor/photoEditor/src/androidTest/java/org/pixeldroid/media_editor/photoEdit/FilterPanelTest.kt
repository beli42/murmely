package org.pixeldroid.media_editor.photoEdit

import android.graphics.Bitmap
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.pixeldroid.media_editor.common.ui.BunnyTheme
import org.pixeldroid.media_editor.photoEdit.customFilters.CustomLayer
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.ElsaLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.SepiaLayer
import org.pixeldroid.media_editor.photoEdit.panel.FilterPanel
import org.pixeldroid.media_editor.photoEdit.panel.FilterTags
import org.pixeldroid.media_editor.photoEdit.panel.sameFilter

/** The filter tab (Compose) on its own */
@RunWith(AndroidJUnit4::class)
class FilterPanelTest {

    @get:Rule val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val custom = CustomLayer("Mine", "void main() {}", uid = 7)
    private val filters: List<ImagineLayer> = listOf(ElsaLayer(), SepiaLayer(), custom)

    @Test fun sameFilterSurvivesNewInstances() {
        assertTrue("built-in by type", sameFilter(ElsaLayer(), ElsaLayer()))
        assertFalse(sameFilter(ElsaLayer(), SepiaLayer()))
        assertTrue("custom by content", sameFilter(custom, custom.copy()))
        assertFalse(sameFilter(custom, custom.copy(source = "other")))
        assertTrue("both none", sameFilter(null, null))
        assertFalse(sameFilter(null, ElsaLayer()))
    }

    @Test fun showsNoneFiltersCustomAndCreate() {
        compose.setContent {
            BunnyTheme {
                FilterPanel(filters, emptyList(), selected = null, applied = emptyList(), vertical = false,
                    onSelect = {}, onApply = {}, onLongPress = {}, onCreate = {})
            }
        }
        compose.onNodeWithText(context.getString(R.string.filterNone)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.filterElsa)).assertIsDisplayed()
        compose.onNodeWithText("Mine").assertIsDisplayed()
        compose.onNodeWithTag(FilterTags.item(0)).assertIsDisplayed()
    }

    @Test fun clickSelectsLongPressShowsAndCreateCreates() {
        var selected: ImagineLayer? = ElsaLayer()
        var longPressed: ImagineLayer? = null
        var created = false
        val thumbnails = List(filters.size + 1) { Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888) }
        compose.setContent {
            BunnyTheme {
                FilterPanel(filters, thumbnails, selected = null, applied = emptyList(), vertical = false,
                    onSelect = { selected = it }, onApply = {}, onLongPress = { longPressed = it }, onCreate = { created = true })
            }
        }
        compose.onNodeWithTag(FilterTags.item(2)).performClick()
        assertTrue("sepia selected", selected is SepiaLayer)
        compose.onNodeWithTag(FilterTags.item(0)).performClick()
        assertEquals("none selected", null, selected)

        compose.onNodeWithTag(FilterTags.item(3)).performTouchInput { longClick() }
        assertEquals(custom, longPressed)

        // The create button is at the end of the row: scroll the row to it first
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag(FilterTags.CREATE))
        compose.onNodeWithTag(FilterTags.CREATE).performClick()
        assertTrue(created)
    }

    @Test fun applyIsOnlyPossibleWhileTryingAFilter() {
        var selected: ImagineLayer? by mutableStateOf(null)
        var appliedCount = 0
        compose.setContent {
            BunnyTheme {
                FilterPanel(filters, emptyList(), selected = selected, applied = listOf(ElsaLayer(), SepiaLayer()),
                    vertical = false, onSelect = {}, onApply = { appliedCount++ }, onLongPress = {}, onCreate = {})
            }
        }
        val elsa = context.getString(R.string.filterElsa)
        val sepia = context.getString(R.string.filterSepia)
        compose.onNodeWithTag(FilterTags.APPLIED).assertTextEquals(context.getString(R.string.applied_filters, "$elsa, $sepia"))
        compose.onNodeWithTag(FilterTags.APPLY).assertIsNotEnabled()
        selected = SepiaLayer()
        compose.onNodeWithTag(FilterTags.APPLY).assertIsEnabled().performClick()
        assertEquals(1, appliedCount)
    }

    /** Filters stack: apply one, try the next on top; undo takes back the last apply */
    @Test fun filtersCanBeStackedAndUndone() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var model: PhotoEditViewModel
        val elsa = ElsaLayer()
        val sepia = SepiaLayer()
        instrumentation.runOnMainSync {
            model = PhotoEditViewModel()
            model.doChange(PhotoEditViewModel.Change.SelectFilter(elsa))
            model.applySelectedFilter()
        }
        assertEquals(listOf<ImagineLayer>(elsa), model.appliedFilters.value)
        assertEquals("after applying, nothing is being tried", null, model.filter.value)

        instrumentation.runOnMainSync {
            model.applySelectedFilter()   // nothing being tried: no change
            model.doChange(PhotoEditViewModel.Change.SelectFilter(sepia))
        }
        assertEquals(sepia, model.filter.value)
        assertEquals("select, apply, select (the empty apply is no change)", 3, model.changes.size)

        instrumentation.runOnMainSync { model.applySelectedFilter() }
        assertEquals(listOf<ImagineLayer>(elsa, sepia), model.appliedFilters.value)

        instrumentation.runOnMainSync { model.undoChange() }
        assertEquals("apply undone", listOf<ImagineLayer>(elsa), model.appliedFilters.value)
        assertEquals("sepia is tried again", sepia, model.filter.value)

        instrumentation.runOnMainSync { model.undoChange() }
        assertEquals(null, model.filter.value)
        instrumentation.runOnMainSync { model.reset() }
        assertTrue(model.appliedFilters.value.isEmpty())
        // Select Elsa, apply, select Sepia, apply
        instrumentation.runOnMainSync { repeat(4) { model.redoChange() } }
        assertEquals("all redone", listOf<ImagineLayer>(elsa, sepia), model.appliedFilters.value)
    }
}

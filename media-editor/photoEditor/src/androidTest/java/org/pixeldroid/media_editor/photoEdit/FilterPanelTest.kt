package org.pixeldroid.media_editor.photoEdit

import android.graphics.Bitmap
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
            BunnyTheme { FilterPanel(filters, emptyList(), selected = null, vertical = false, {}, {}, {}) }
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
                FilterPanel(filters, thumbnails, selected = null, vertical = false,
                    onSelect = { selected = it }, onLongPress = { longPressed = it }, onCreate = { created = true })
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
}

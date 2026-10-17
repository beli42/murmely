package org.pixeldroid.media_editor.photoEdit

import org.pixeldroid.media_editor.photoEdit.imagine.layers.SwirlLayer
import org.pixeldroid.media_editor.photoEdit.panel.FilterCategory
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
import androidx.compose.ui.test.performTouchInput
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
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.base
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ConfiguredFilter
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.FilterSettings
import org.pixeldroid.media_editor.photoEdit.imagine.layers.MirrorHorizontalLayer
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
        assertTrue("settings don't matter", sameFilter(ConfiguredFilter(ElsaLayer(), FilterSettings(strength = 0.3f)), ElsaLayer()))
    }

    private val swirl = SwirlLayer()
    private val groups = mapOf(
        FilterCategory.COLOR to listOf<ImagineLayer>(ElsaLayer(), SepiaLayer()),
        FilterCategory.GEOMETRY to listOf<ImagineLayer>(swirl),
        FilterCategory.CUSTOM to listOf<ImagineLayer>(custom),
    )

    private fun panel(
        selected: () -> ImagineLayer? = { null },
        applied: List<ImagineLayer> = emptyList(),
        onSelect: (ImagineLayer?) -> Unit = {},
        onApply: () -> Unit = {},
        onLongPress: (ImagineLayer?) -> Unit = {},
        onCreate: () -> Unit = {},
        onSettingsChange: (FilterSettings) -> Unit = {},
    ) = compose.setContent {
        BunnyTheme {
            FilterPanel(groups, thumbnailOf = { null }, selected = selected(), applied = applied, vertical = false,
                onSelect = onSelect, onApply = onApply, onLongPress = onLongPress, onCreate = onCreate,
                onSettingsChange = onSettingsChange)
        }
    }

    /** Strength slider only for a filter being tried that has a strength (the centre is set on the photo) */
    @Test fun strengthOnlyWhereTheFilterHasOne() {
        var selected: ImagineLayer? by mutableStateOf(ElsaLayer())
        var changed: FilterSettings? = null
        panel(selected = { selected }, onSettingsChange = { changed = it })

        // Colour filter: strength
        compose.onNodeWithTag(FilterTags.STRENGTH).assertIsDisplayed()
        compose.onNodeWithTag(FilterTags.STRENGTH_VALUE).assertTextEquals("100 %")

        // Swirl at 50 %: tapping the value goes back to 100 %
        selected = ConfiguredFilter(SwirlLayer(), FilterSettings(strength = 0.5f))
        compose.onNodeWithTag(FilterTags.STRENGTH_VALUE).assertTextEquals("50 %").performClick()
        assertEquals(1f, changed!!.strength, 0f)

        // Mirror: no strength (its line is set on the photo)
        selected = MirrorHorizontalLayer()
        compose.onNodeWithTag(FilterTags.STRENGTH).assertDoesNotExist()

        // Nothing being tried: none
        selected = null
        compose.onNodeWithTag(FilterTags.STRENGTH).assertDoesNotExist()
    }

    @Test fun threeGroupsWithTheirFilters() {
        panel()
        // Colour first
        compose.onNodeWithText(context.getString(R.string.filterNone)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.filterElsa)).assertIsDisplayed()
        compose.onNodeWithTag(FilterTags.CREATE).assertDoesNotExist()

        compose.onNodeWithTag(FilterTags.category(FilterCategory.GEOMETRY)).performClick()
        compose.onNodeWithText(context.getString(R.string.filterSwirl)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.filterElsa)).assertDoesNotExist()

        compose.onNodeWithTag(FilterTags.category(FilterCategory.CUSTOM)).performClick()
        compose.onNodeWithText("Mine").assertIsDisplayed()
        compose.onNodeWithTag(FilterTags.CREATE).assertIsDisplayed()
    }

    @Test fun opensTheGroupOfTheChosenFilter() {
        panel(selected = { swirl })
        compose.onNodeWithText(context.getString(R.string.filterSwirl)).assertIsDisplayed()
    }

    @Test fun clickSelectsLongPressShowsAndCreateCreates() {
        var selected: ImagineLayer? = ElsaLayer()
        var longPressed: ImagineLayer? = null
        var created = false
        panel(onSelect = { selected = it }, onLongPress = { longPressed = it }, onCreate = { created = true })

        compose.onNodeWithTag(FilterTags.item(2)).performClick()
        assertTrue("sepia selected", selected is SepiaLayer)
        compose.onNodeWithTag(FilterTags.item(0)).performClick()
        assertEquals("none selected", null, selected)

        compose.onNodeWithTag(FilterTags.category(FilterCategory.CUSTOM)).performClick()
        compose.onNodeWithTag(FilterTags.item(1)).performTouchInput { longClick() }
        assertEquals(custom, longPressed)
        compose.onNodeWithTag(FilterTags.CREATE).performClick()
        assertTrue(created)
    }

    @Test fun applyIsOnlyPossibleWhileTryingAFilter() {
        var selected: ImagineLayer? by mutableStateOf(null)
        var appliedCount = 0
        panel(selected = { selected }, applied = listOf(ElsaLayer(), SepiaLayer()), onApply = { appliedCount++ })
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
            // Trying is no step in the history
            model.doChange(PhotoEditViewModel.Change.SelectFilter(sepia))
            model.doChange(PhotoEditViewModel.Change.SelectFilter(elsa))
        }
        assertEquals("only tried: no step", 0, model.changes.size)
        instrumentation.runOnMainSync { model.applySelectedFilter() }
        assertEquals(listOf<ImagineLayer>(elsa), model.appliedFilters.value.map { it.base })
        assertEquals("after applying, nothing is being tried", null, model.filter.value)

        instrumentation.runOnMainSync {
            model.applySelectedFilter()   // nothing being tried: no change
            model.doChange(PhotoEditViewModel.Change.SelectFilter(sepia))
            model.applySelectedFilter()
        }
        assertEquals(listOf<ImagineLayer>(elsa, sepia), model.appliedFilters.value.map { it.base })
        assertEquals("two applies, nothing else", 2, model.changes.size)

        instrumentation.runOnMainSync { model.undoChange() }
        assertEquals("apply undone", listOf<ImagineLayer>(elsa), model.appliedFilters.value.map { it.base })
        assertEquals("and nothing is tried", null, model.filter.value)

        // Undo leaves a filter being tried as it is
        instrumentation.runOnMainSync {
            model.doChange(PhotoEditViewModel.Change.SelectFilter(sepia))
            model.undoChange()
        }
        assertTrue(model.appliedFilters.value.isEmpty())
        assertEquals(sepia, model.filter.value?.base)

        // Reset: back to the original, nothing tried either
        instrumentation.runOnMainSync { model.reset() }
        assertEquals(null, model.filter.value)
        instrumentation.runOnMainSync { repeat(2) { model.redoChange() } }
        assertEquals("all redone", listOf<ImagineLayer>(elsa, sepia), model.appliedFilters.value.map { it.base })
    }
}

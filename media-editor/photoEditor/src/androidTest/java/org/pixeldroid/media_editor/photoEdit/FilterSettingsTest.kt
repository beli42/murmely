package org.pixeldroid.media_editor.photoEdit

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.pixeldroid.media_editor.photoEdit.PhotoEditViewModel.Change
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.FilterSettings
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.MirrorPart
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.base
import org.pixeldroid.media_editor.photoEdit.imagine.layers.ElsaLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.MirrorHorizontalLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.SepiaLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.SwirlLayer

/** Strength and centre of the filter being tried: live changes, edit history, apply */
@RunWith(AndroidJUnit4::class)
class FilterSettingsTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var model: PhotoEditViewModel
    private val swirl = SwirlLayer()

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    @Before fun createModel() = onMain { model = PhotoEditViewModel() }

    /** Trying strength and centre is no step; applying is, with the settings of that moment */
    @Test fun settingsAreKeptWhenApplied() {
        onMain {
            model.doChange(Change.SelectFilter(swirl))
            model.doChange(Change.FilterSettingsChange(FilterSettings(strength = 1.5f)))
            model.doChange(Change.FilterSettingsChange(FilterSettings(strength = 1.5f, centerX = 0.2f, centerY = 0.3f)))
        }
        assertEquals(FilterSettings(1.5f, 0.2f, 0.3f), model.filter.value!!.settings)
        assertSame("the filter object itself is unchanged", swirl, model.filter.value!!.base)
        assertEquals("only tried: no step", 0, model.changes.size)

        onMain { model.applySelectedFilter() }
        assertEquals(1, model.changes.size)
        assertEquals(1.5f, model.appliedFilters.value.single().shaderStrength, 0f)

        onMain { model.undoChange() }
        assertTrue(model.appliedFilters.value.isEmpty())
        onMain { model.redoChange() }
        assertEquals(1.5f, model.appliedFilters.value.single().shaderStrength, 0f)
    }

    @Test fun choosingTheSameFilterAgainKeepsItsSettings() {
        onMain {
            model.doChange(Change.SelectFilter(swirl))
            model.doChange(Change.FilterSettingsChange(FilterSettings(strength = 0.4f)))
            model.doChange(Change.SelectFilter(SwirlLayer()))   // e.g. a new object after rotating
        }
        assertEquals(0.4f, model.filter.value!!.settings.strength, 0f)
        assertEquals("trying is no step", 0, model.changes.size)

        // Another filter starts with its defaults
        onMain { model.doChange(Change.SelectFilter(ElsaLayer())) }
        assertEquals(FilterSettings.DEFAULT, model.filter.value!!.settings)
    }

    @Test fun sameFilterAppliedTwiceKeepsBothSettings() {
        onMain {
            model.doChange(Change.SelectFilter(swirl, FilterSettings(strength = 0.5f)))
            model.applySelectedFilter()
            model.doChange(Change.SelectFilter(swirl, FilterSettings(strength = 2f)))
            model.applySelectedFilter()
        }
        val applied = model.appliedFilters.value
        assertEquals(listOf(0.5f, 2f), applied.map { it.shaderStrength })
        assertTrue("both use the same filter", applied.all { it.base === swirl })
    }

    @Test fun strengthAndCentreAreLimitedToTheFilter() {
        onMain {
            model.doChange(Change.SelectFilter(ElsaLayer()))
            model.onFilterSettingsChange(FilterSettings(strength = 1.8f, centerX = 0.1f))
        }
        // Colour filter: at most 100 %, no centre
        assertEquals(FilterSettings(strength = 1f), model.filter.value!!.settings)
        assertEquals(1f, model.filter.value!!.intensity, 0f)

        onMain {
            model.doChange(Change.SelectFilter(swirl))
            model.onFilterSettingsChange(FilterSettings(strength = 3f, centerX = -1f, centerY = 0.7f))
        }
        assertEquals(FilterSettings(strength = 2f, centerX = 0f, centerY = 0.7f), model.filter.value!!.settings)
    }

    @Test fun liveChangesAreNoSteps() {
        onMain {
            model.doChange(Change.SelectFilter(swirl))
            model.onFilterSettingsChange(FilterSettings(strength = 0.3f))
            model.onFilterCenterChange(0.6f, 0.4f)
        }
        assertEquals(FilterSettings(0.3f, 0.6f, 0.4f), model.filter.value!!.settings)
        // Not even after a while
        Thread.sleep(1_000)
        assertEquals(0, model.changes.size)
    }

    @Test fun anotherFilterStartsWithItsDefaults() {
        onMain {
            model.doChange(Change.SelectFilter(swirl))
            model.onFilterSettingsChange(FilterSettings(strength = 0.3f))
            model.doChange(Change.SelectFilter(SepiaLayer()))
        }
        assertEquals(FilterSettings.DEFAULT, model.filter.value!!.settings)
        assertEquals(0, model.changes.size)
    }

    /** Applied right after a change: applied with it; undoing the apply removes the filter */
    @Test fun applyTakesTheCurrentSettings() {
        onMain {
            model.doChange(Change.SelectFilter(MirrorHorizontalLayer()))
            model.onFilterCenterChange(0.8f, 0.5f)
            model.applySelectedFilter()
        }
        assertEquals(0.8f, model.appliedFilters.value.single().centerX, 0f)
        assertNull(model.filter.value)
        assertEquals(1, model.changes.size)
        onMain { model.undoChange() }
        assertTrue(model.appliedFilters.value.isEmpty())
        assertNull(model.filter.value)
    }

    /** The first move of a mirror's line decides which part is mirrored; reset forgets it */
    @Test fun firstMoveDecidesTheMirroredPart() {
        onMain {
            model.doChange(Change.SelectFilter(MirrorHorizontalLayer()))
            // Moved right first: the left part is kept
            model.onFilterCenterChange(0.7f, 0.5f)
            model.onFilterCenterChange(0.3f, 0.5f)
        }
        assertEquals(MirrorPart.START, model.filter.value!!.settings.mirrorPart)

        // The reset button of the undo controls, in the filter tab
        onMain { model.resetHere() }
        assertEquals(FilterSettings.DEFAULT, model.filter.value!!.settings)

        // Moved left first: the right part is kept
        onMain { model.onFilterCenterChange(0.4f, 0.5f) }
        assertEquals(MirrorPart.END, model.filter.value!!.settings.mirrorPart)

        // Choosing another filter and the mirror again starts afresh
        onMain {
            model.doChange(Change.SelectFilter(SepiaLayer()))
            model.doChange(Change.SelectFilter(MirrorHorizontalLayer()))
        }
        assertEquals(MirrorPart.UNDECIDED, model.filter.value!!.settings.mirrorPart)

        // Top / bottom mirror: moved up first keeps the bottom part
        onMain {
            model.doChange(Change.SelectFilter(org.pixeldroid.media_editor.photoEdit.imagine.layers.MirrorVerticalLayer()))
            model.onFilterCenterChange(0.5f, 0.2f)
        }
        assertEquals(MirrorPart.END, model.filter.value!!.settings.mirrorPart)
    }

    /** Two fingers set the size: limited, part of trying (no step), back with reset, kept when applied */
    @Test fun sizeOfTheArea() {
        onMain {
            model.doChange(Change.SelectFilter(swirl))
            model.onFilterSizeChange(5f)
        }
        assertEquals("at most three times", 3f, model.filter.value!!.settings.size, 0f)
        assertEquals("trying is no step", 0, model.changes.size)
        onMain { model.onFilterSizeChange(0.5f) }
        assertEquals(0.5f, model.filter.value!!.settings.size, 0f)
        onMain { model.applySelectedFilter() }
        assertEquals(0.5f, model.appliedFilters.value.single().shaderSize, 0f)
        onMain {
            model.doChange(Change.SelectFilter(SepiaLayer()))
            model.onFilterSizeChange(2f)
        }
        assertEquals("a colour filter has no area", 1f, model.filter.value!!.settings.size, 0f)
        onMain {
            model.doChange(Change.SelectFilter(MirrorHorizontalLayer()))
            model.doChange(Change.SelectFilter(SwirlLayer()))
            model.onFilterSizeChange(2f)
            model.resetHere()
        }
        assertEquals("reset", 1f, model.filter.value!!.settings.size, 0f)
    }
}

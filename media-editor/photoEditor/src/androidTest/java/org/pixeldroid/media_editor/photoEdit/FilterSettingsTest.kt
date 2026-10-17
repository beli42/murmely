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

    @Test fun settingsAreRecordedAndUndone() {
        onMain {
            model.doChange(Change.SelectFilter(swirl))
            model.doChange(Change.FilterSettingsChange(FilterSettings(strength = 1.5f)))
            model.doChange(Change.FilterSettingsChange(FilterSettings(strength = 1.5f, centerX = 0.2f, centerY = 0.3f)))
        }
        assertEquals(FilterSettings(1.5f, 0.2f, 0.3f), model.filter.value!!.settings)
        assertSame("the filter object itself is unchanged", swirl, model.filter.value!!.base)

        onMain { model.undoChange() }
        assertEquals(FilterSettings(strength = 1.5f), model.filter.value!!.settings)
        onMain { model.undoChange() }
        assertEquals(FilterSettings.DEFAULT, model.filter.value!!.settings)
        onMain { model.redoChange(); model.redoChange() }
        assertEquals(FilterSettings(1.5f, 0.2f, 0.3f), model.filter.value!!.settings)
    }

    @Test fun choosingTheSameFilterAgainKeepsItsSettings() {
        onMain {
            model.doChange(Change.SelectFilter(swirl))
            model.doChange(Change.FilterSettingsChange(FilterSettings(strength = 0.4f)))
            model.doChange(Change.SelectFilter(SwirlLayer()))   // e.g. a new object after rotating
        }
        assertEquals(0.4f, model.filter.value!!.settings.strength, 0f)
        assertEquals("no change recorded", 2, model.changes.size)

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

    @Test fun liveChangesAreRecordedOnceTheyStop() {
        onMain {
            model.doChange(Change.SelectFilter(swirl))
            model.onFilterSettingsChange(FilterSettings(strength = 0.3f))
            model.onFilterCenterChange(0.6f, 0.4f)
        }
        // Shown right away, but not recorded yet
        assertEquals(FilterSettings(0.3f, 0.6f, 0.4f), model.filter.value!!.settings)
        assertEquals(1, model.changes.size)

        waitUntil { model.changes.size == 2 }
        assertEquals(Change.FilterSettingsChange(FilterSettings(0.3f, 0.6f, 0.4f)), model.changes.last())
    }

    @Test fun liveChangesOfAFilterNoLongerTriedAreDropped() {
        onMain {
            model.doChange(Change.SelectFilter(swirl))
            model.onFilterSettingsChange(FilterSettings(strength = 0.3f))
            model.doChange(Change.SelectFilter(SepiaLayer()))
        }
        Thread.sleep(DEBOUNCE_MS + 300)
        assertEquals("select, select", 2, model.changes.size)
        assertEquals(FilterSettings.DEFAULT, model.filter.value!!.settings)
    }

    /** Applying right after a change: the change is recorded first, undoing the apply keeps it */
    @Test fun applyRecordsPendingSettings() {
        onMain {
            model.doChange(Change.SelectFilter(MirrorHorizontalLayer()))
            model.onFilterCenterChange(0.8f, 0.5f)
            model.applySelectedFilter()
        }
        assertEquals(0.8f, model.appliedFilters.value.single().centerX, 0f)
        assertNull(model.filter.value)
        onMain { model.undoChange() }
        assertEquals("tried again as it was applied", 0.8f, model.filter.value!!.settings.centerX, 0f)
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

        onMain { model.resetFilterCenter() }
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

    private fun waitUntil(condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (!condition()) {
            assertTrue("timed out", System.currentTimeMillis() < end)
            Thread.sleep(50)
        }
    }

    private companion object {
        /** See PhotoEditViewModel.filterSettingsChange */
        const val DEBOUNCE_MS = 750L
    }
}

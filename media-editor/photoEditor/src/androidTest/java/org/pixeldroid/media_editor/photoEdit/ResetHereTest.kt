package org.pixeldroid.media_editor.photoEdit

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.pixeldroid.media_editor.photoEdit.PhotoEditViewModel.Change
import org.pixeldroid.media_editor.photoEdit.PhotoEditViewModel.ShownView
import org.pixeldroid.media_editor.photoEdit.canvas.CanvasExtension
import org.pixeldroid.media_editor.photoEdit.frame.FrameSettings
import org.pixeldroid.media_editor.photoEdit.frame.FrameStyle
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.FilterSettings
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.base
import org.pixeldroid.media_editor.photoEdit.imagine.layers.ElsaLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.SwirlLayer
import org.pixeldroid.media_editor.photoEdit.overlay.RelativePoint

/** Reset only resets the area the user is in, and can be undone */
@RunWith(AndroidJUnit4::class)
class ResetHereTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var model: PhotoEditViewModel

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    /** A bit of everything */
    @Before fun editEverything() = onMain {
        model = PhotoEditViewModel()
        model.doChange(Change.SelectFilter(ElsaLayer()))
        model.applySelectedFilter()
        model.doChange(Change.SlidersChange(Sliders().with(Adjustment.CONTRAST, 0.5f)))
        model.addStroke(listOf(RelativePoint(0.1f, 0.1f), RelativePoint(0.2f, 0.2f)))
        model.addText("Hi", 0.5f, 0.5f)
        model.addSticker("file:///android_asset/stickers/noto/1f430.webp")
        model.chooseFrame(FrameSettings.of(FrameStyle.SOLID))
        model.chooseCanvas(CanvasExtension(size = 0.2f))
    }

    private fun resetIn(tab: EditorTab, tool: ShownView? = null) = onMain {
        model.selectTab(tab)
        if (tool != null) model.toggleTool(tool)
        model.resetHere()
    }

    /** Everything except what [except] names is still there */
    private fun assertOthersKept(except: String) {
        if (except != "filters") assertEquals(1, model.appliedFilters.value.size)
        if (except != "sliders") assertEquals(0.5f, model.sliders.value[Adjustment.CONTRAST], 0f)
        if (except != "drawings") assertEquals(1, model.overlay.value.strokes.size)
        if (except != "texts") assertEquals(1, model.overlay.value.texts.size)
        if (except != "stickers") assertEquals(1, model.overlay.value.stickers.size)
        if (except != "frame") assertTrue(model.frame.value != null)
        if (except != "canvas") assertTrue(model.canvas.value != null)
    }

    @Test fun appliedFilters() {
        resetIn(EditorTab.FILTERS)
        assertTrue(model.appliedFilters.value.isEmpty())
        assertOthersKept("filters")
        onMain { model.undoChange() }
        assertEquals(1, model.appliedFilters.value.size)
    }

    @Test fun filterBeingTriedOnlyGetsItsSettingsBack() {
        onMain {
            model.doChange(Change.SelectFilter(SwirlLayer(), FilterSettings(strength = 0.3f, centerX = 0.2f)))
        }
        val steps = model.changes.size
        resetIn(EditorTab.FILTERS)
        assertEquals("still tried", SwirlLayer::class, model.filter.value!!.base::class)
        assertEquals(FilterSettings.DEFAULT, model.filter.value!!.settings)
        assertEquals("applied filters kept", 1, model.appliedFilters.value.size)
        assertEquals("trying is no step", steps, model.changes.size)
    }

    @Test fun sliders() {
        resetIn(EditorTab.SLIDERS)
        assertTrue(model.sliders.value.isNeutral)
        assertOthersKept("sliders")
        onMain { model.undoChange() }
        assertEquals(0.5f, model.sliders.value[Adjustment.CONTRAST], 0f)
    }

    @Test fun eachToolResetsWhatItMakes() {
        for ((tool, what) in listOf(
            ShownView.Draw to "drawings", ShownView.Text to "texts", ShownView.Sticker to "stickers",
            ShownView.Frame to "frame", ShownView.Canvas to "canvas",
        )) {
            onMain { model.selectTab(EditorTab.EDIT); if (model.shownView.value != tool) model.toggleTool(tool) }
            assertTrue("$what can be reset", model.canResetHere())
            onMain { model.resetHere() }
            assertOthersKept(what)
            assertFalse("$what: nothing more to reset", model.canResetHere())
            onMain { model.undoChange() }
            assertOthersKept("")
        }
    }

    @Test fun nothingWithoutATool() {
        onMain { model.selectTab(EditorTab.EDIT) }
        assertFalse(model.canResetHere())
        onMain { model.resetHere() }
        assertOthersKept("")
    }
}

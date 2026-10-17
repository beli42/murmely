package org.pixeldroid.media_editor.photoEdit

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.pixeldroid.media_editor.photoEdit.canvas.CanvasExtension
import org.pixeldroid.media_editor.photoEdit.canvas.Side

/** The canvas in the edit history, and texts kept on the canvas instead of the photo */
@RunWith(AndroidJUnit4::class)
class CanvasHistoryTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun canvasInTheHistory() {
        lateinit var model: PhotoEditViewModel
        val below = CanvasExtension(size = 0.2f, sides = setOf(Side.BOTTOM))
        instrumentation.runOnMainSync {
            model = PhotoEditViewModel()
            model.chooseCanvas(below)
            model.adjustCanvas(below.copy(size = 0.3f))
        }
        assertEquals(0.3f, model.canvas.value!!.size, 0f)
        val end = System.currentTimeMillis() + 5_000
        while (model.changes.size < 2 && System.currentTimeMillis() < end) Thread.sleep(50)
        assertEquals("choose, adjust", 2, model.changes.size)
        instrumentation.runOnMainSync { model.undoChange(); model.undoChange() }
        assertNull(model.canvas.value)
    }

    @Test fun captionsMayGoOnTheAddedArea() {
        lateinit var model: PhotoEditViewModel
        instrumentation.runOnMainSync {
            model = PhotoEditViewModel()
            model.photoSize = 1000 to 1000
            model.imageAspect = 1f
            model.chooseCanvas(CanvasExtension(size = 0.3f, sides = setOf(Side.BOTTOM)))
            model.toggleTool(PhotoEditViewModel.ShownView.Text)
            model.startNewText(0.5f, 1.15f)
            model.updateTextEdit("Unterschrift")
            model.finishTextEdit()
        }
        val caption = model.overlay.value.texts.single()
        assertTrue("stays below the photo: ${caption.y}", caption.y > 1f)
        assertTrue("on the canvas: ${caption.y}", caption.y < 1.3f)
    }
}

package org.pixeldroid.media_editor.photoEdit

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.pixeldroid.media_editor.photoEdit.frame.FramePlacement
import org.pixeldroid.media_editor.photoEdit.frame.FrameRenderer
import org.pixeldroid.media_editor.photoEdit.frame.FrameSettings
import org.pixeldroid.media_editor.photoEdit.frame.FrameStyle

/** Frames drawn pixel by pixel, and in the edit history */
@RunWith(AndroidJUnit4::class)
class FrameTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    /** 400 x 300, blue */
    private fun photo() = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }

    /** Red, 10 % of the shorter side (30 px) */
    private fun red(style: FrameStyle, placement: FramePlacement) =
        FrameSettings(style, placement, size = 0.1f, hue = 0f, lightness = 0.5f)

    private fun assertColor(bitmap: Bitmap, x: Int, y: Int, color: Int, what: String) =
        ImageIntegrity.assertColor(bitmap, x, y, color, 8, what)

    @Test fun outsideMakesTheImageLargerAndKeepsAllOfThePhoto() {
        val framed = FrameRenderer.apply(photo(), red(FrameStyle.SOLID, FramePlacement.OUTSIDE))
        assertEquals(460 to 360, framed.width to framed.height)
        assertColor(framed, 5, 5, Color.RED, "frame")
        assertColor(framed, 32, 32, Color.BLUE, "photo's corner, uncovered")
        assertColor(framed, 230, 180, Color.BLUE, "middle")
    }

    @Test fun insideCoversTheEdgeOnly() {
        val framed = FrameRenderer.apply(photo(), red(FrameStyle.SOLID, FramePlacement.INSIDE))
        assertEquals(400 to 300, framed.width to framed.height)
        assertColor(framed, 5, 5, Color.RED, "edge covered")
        assertColor(framed, 28, 150, Color.RED, "just inside the frame")
        assertColor(framed, 32, 150, Color.BLUE, "just inside the window")
        assertColor(framed, 200, 150, Color.BLUE, "middle")
    }

    @Test fun polaroidIsWiderAtTheBottom() {
        val framed = FrameRenderer.apply(photo(), red(FrameStyle.POLAROID, FramePlacement.OUTSIDE))
        // 30 px on three sides, 105 px at the bottom
        assertEquals(460 to 435, framed.width to framed.height)
        assertColor(framed, 230, 320, Color.BLUE, "photo above the wide part")
        assertColor(framed, 230, 400, Color.RED, "wide part")
    }

    @Test fun roundedCoversThePhotosCorners() {
        val framed = FrameRenderer.apply(photo(), red(FrameStyle.ROUNDED, FramePlacement.OUTSIDE))
        assertColor(framed, 31, 31, Color.RED, "rounded-off corner of the photo")
        assertColor(framed, 230, 31, Color.BLUE, "top edge of the photo, not a corner")
    }

    @Test fun filmHasPerforation() {
        val framed = FrameRenderer.apply(photo(), FrameSettings.of(FrameStyle.FILM, size = 0.1f))
        // Black band at the top, 42 px high: holes in it, so both black and light pixels
        val row = (0 until framed.width).map { framed.getPixel(it, 21) }
        assertTrue("black between the holes", row.any { Color.red(it) < 40 })
        assertTrue("light holes", row.any { Color.red(it) > 180 })
        assertColor(framed, framed.width / 2, framed.height / 2, Color.BLUE, "photo")
    }

    @Test fun framesInTheHistory() {
        lateinit var model: PhotoEditViewModel
        val polaroid = FrameSettings.of(FrameStyle.POLAROID)
        instrumentation.runOnMainSync {
            model = PhotoEditViewModel()
            model.chooseFrame(polaroid)
        }
        assertEquals(polaroid, model.frame.value)
        assertEquals(1, model.changes.size)

        // Sliders: shown right away, recorded once they stop
        instrumentation.runOnMainSync {
            model.adjustFrame(polaroid.copy(size = 0.1f))
            model.adjustFrame(polaroid.copy(size = 0.12f))
        }
        assertEquals(0.12f, model.frame.value!!.size, 0f)
        val end = System.currentTimeMillis() + 5_000
        while (model.changes.size < 2 && System.currentTimeMillis() < end) Thread.sleep(50)
        assertEquals("choose, adjust", 2, model.changes.size)

        instrumentation.runOnMainSync { model.undoChange() }
        assertEquals(polaroid, model.frame.value)
        instrumentation.runOnMainSync { model.chooseFrame(null) }
        assertNull(model.frame.value)
        instrumentation.runOnMainSync { model.undoChange() }
        assertEquals("removing it is undone too", polaroid, model.frame.value)
    }
}

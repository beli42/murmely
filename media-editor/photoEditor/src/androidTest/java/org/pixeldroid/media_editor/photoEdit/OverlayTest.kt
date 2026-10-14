package org.pixeldroid.media_editor.photoEdit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.pixeldroid.media_editor.photoEdit.overlay.Overlay
import org.pixeldroid.media_editor.photoEdit.overlay.OverlayDefaults
import org.pixeldroid.media_editor.photoEdit.overlay.OverlayRenderer
import org.pixeldroid.media_editor.photoEdit.overlay.RelativePoint
import org.pixeldroid.media_editor.photoEdit.overlay.StickerItem
import org.pixeldroid.media_editor.photoEdit.overlay.Stroke
import org.pixeldroid.media_editor.photoEdit.overlay.TextItem
import org.pixeldroid.media_editor.photoEdit.overlay.ToolStyle

/** Drawing the overlay (preview and saved file) and keeping it in the edit history */
@RunWith(AndroidJUnit4::class)
class OverlayTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun white(width: Int, height: Int) =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }

    /** A horizontal line through the middle, as the user would draw it */
    private val line = Stroke(listOf(RelativePoint(0.1f, 0.5f), RelativePoint(0.9f, 0.5f)))

    @Test fun strokeIsDrawnAtTheSameRelativePlaceAtAnySize() {
        // Preview-like size and full image size give the same picture
        for ((w, h) in listOf(400 to 300, 4000 to 3000)) {
            val bitmap = white(w, h)
            OverlayRenderer.draw(Canvas(bitmap), w, h, Overlay(strokes = listOf(line)))
            ImageIntegrity.assertColor(bitmap, w / 2, h / 2, Color.RED, 10, "middle of the line at $w x $h")
            ImageIntegrity.assertColor(bitmap, w / 2, h / 4, Color.WHITE, 0, "above the line at $w x $h")
            ImageIntegrity.assertColor(bitmap, w / 20, h / 2, Color.WHITE, 0, "before the start at $w x $h")
        }
    }

    /** The line is 0.6 % of the image width thick, at any size */
    @Test fun strokeWidthScalesWithTheImage() {
        fun thickness(w: Int, h: Int): Int {
            val bitmap = white(w, h)
            OverlayRenderer.draw(Canvas(bitmap), w, h, Overlay(strokes = listOf(line)))
            // Count every pixel the line touches (edges are anti-aliased, so not fully red)
            return (0 until h).count { y -> bitmap.getPixel(w / 2, y) != Color.WHITE }
        }
        // Sizes where the line is thick enough to measure; at a few pixels the anti-aliased
        // edges dominate the measurement
        for (w in listOf(2000, 5000)) {
            val expected = OverlayDefaults.STROKE_WIDTH * w
            assertEquals("line thickness at $w px", expected, thickness(w, w).toFloat(), 2f)
        }
    }

    @Test fun textIsDrawnAtItsPosition() {
        val bitmap = white(1000, 1000)
        OverlayRenderer.draw(Canvas(bitmap), 1000, 1000, Overlay(texts = listOf(TextItem("████", 0.2f, 0.6f))))
        // Text height 10 % of the width, baseline at y = 600
        val reds = (200 until 600).count { x -> bitmap.getPixel(x, 570) == Color.RED || Color.red(bitmap.getPixel(x, 570)) > 200 && Color.green(bitmap.getPixel(x, 570)) < 80 }
        assertTrue("text pixels on the line above the baseline", reds > 20)
        ImageIntegrity.assertColor(bitmap, 500, 300, Color.WHITE, 0, "far above the text")
    }

    @Test fun stickerIsCentredAndScaled() = runBlocking {
        val blue = TestImages.write(context, "sticker.png", TestImages.solid(200, 100, Color.BLUE))
        val overlay = Overlay(stickers = listOf(StickerItem(blue.toString(), 0.5f, 0.5f, width = 0.4f)))
        val bitmap = white(1000, 1000)
        val stickers = OverlayRenderer.loadStickers(context, overlay, bitmap.width)
        OverlayRenderer.draw(Canvas(bitmap), 1000, 1000, overlay, stickers)
        // 400 x 200 px, centred: x 300..700, y 400..600
        ImageIntegrity.assertColor(bitmap, 500, 500, Color.BLUE, 8, "centre")
        ImageIntegrity.assertColor(bitmap, 320, 420, Color.BLUE, 8, "inside the top left corner")
        ImageIntegrity.assertColor(bitmap, 280, 500, Color.WHITE, 0, "left of the sticker")
        ImageIntegrity.assertColor(bitmap, 500, 380, Color.WHITE, 0, "above the sticker (aspect ratio kept)")
    }

    @Test fun overlayFollowsUndoRedoAndReset() {
        lateinit var model: PhotoEditViewModel
        instrumentation.runOnMainSync {
            model = PhotoEditViewModel()
            model.addStroke(line.points)
            model.addText("Hi", 0.5f, 0.5f)
            model.addSticker("content://test/sticker".toUri(), 0.3f, 0.3f)
        }
        assertEquals(1 to 1, model.overlay.value.strokes.size to model.overlay.value.texts.size)
        assertEquals(1, model.overlay.value.stickers.size)

        instrumentation.runOnMainSync { model.undoChange() }
        assertTrue("sticker undone", model.overlay.value.stickers.isEmpty())
        assertEquals("text kept", 1, model.overlay.value.texts.size)

        instrumentation.runOnMainSync { model.redoChange() }
        assertEquals("sticker redone", 1, model.overlay.value.stickers.size)

        instrumentation.runOnMainSync { model.reset() }
        assertTrue("all undone", model.overlay.value.isEmpty)

        instrumentation.runOnMainSync { repeat(3) { model.redoChange() } }
        assertFalse("all redone", model.overlay.value.isEmpty)
        assertEquals(line, model.overlay.value.strokes.single())
    }

    @Test fun chosenColourAndSizeAreUsedForNewItems() {
        lateinit var model: PhotoEditViewModel
        val blue = ToolStyle(hue = 240f, lightness = 0.5f, strokeWidth = 0.02f, textSize = 0.15f)
        instrumentation.runOnMainSync {
            model = PhotoEditViewModel()
            model.addStroke(line.points)               // default red
            model.onToolStyleChange(blue)
            model.addStroke(line.points)
            model.addText("Hi", 0.5f, 0.5f)
        }
        val (first, second) = model.overlay.value.strokes
        assertEquals("earlier lines keep their colour", OverlayDefaults.COLOR, first.color)
        assertEquals(Color.BLUE, second.color)
        assertEquals(0.02f, second.width, 0f)
        assertEquals(Color.BLUE, model.overlay.value.texts.single().color)
        assertEquals(0.15f, model.overlay.value.texts.single().size, 0f)
    }

    @Test fun movingAndEditingCanBeUndone() {
        lateinit var model: PhotoEditViewModel
        instrumentation.runOnMainSync {
            model = PhotoEditViewModel()
            model.addText("Hello", 0.2f, 0.3f)
            model.addSticker("content://test/sticker".toUri(), 0.5f, 0.5f, aspect = 2f)
        }
        val text = model.overlay.value.texts.single()
        val sticker = model.overlay.value.stickers.single()
        assertEquals(2f, sticker.aspect, 0f)
        assertTrue("ids differ", text.id != sticker.id)

        instrumentation.runOnMainSync {
            model.moveText(text.id, 0.6f, 0.7f)
            model.editText(text.id, "World")
            model.moveSticker(sticker.id, 0.1f, 0.1f)
        }
        model.overlay.value.texts.single().let {
            assertEquals("World", it.text)
            assertEquals(0.6f to 0.7f, it.x to it.y)
        }
        assertEquals(0.1f, model.overlay.value.stickers.single().x, 0f)

        instrumentation.runOnMainSync { model.undoChange(); model.undoChange() }
        assertEquals("sticker back", 0.5f, model.overlay.value.stickers.single().x, 0f)
        assertEquals("text back", "Hello", model.overlay.value.texts.single().text)
        assertEquals("still moved", 0.6f, model.overlay.value.texts.single().x, 0f)

        instrumentation.runOnMainSync { model.editText(text.id, "") }
        assertTrue("empty text removes it", model.overlay.value.texts.isEmpty())
        instrumentation.runOnMainSync { model.undoChange() }
        assertEquals("removal undone", 1, model.overlay.value.texts.size)
    }

    @Test fun emptyInputIsIgnored() {
        lateinit var model: PhotoEditViewModel
        instrumentation.runOnMainSync {
            model = PhotoEditViewModel()
            model.addStroke(emptyList())
            model.addText("   ", 0.5f, 0.5f)
        }
        assertTrue(model.overlay.value.isEmpty)
        assertTrue("nothing to undo", model.changes.isEmpty())
    }
}

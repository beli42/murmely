package org.pixeldroid.media_editor.photoEdit.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayTest {

    @Test fun emptyOverlay() = assertTrue(Overlay().isEmpty)

    @Test fun addingItems() {
        val overlay = Overlay() + Stroke(listOf(RelativePoint(0f, 0f), RelativePoint(1f, 1f))) +
            TextItem("Hi", 0.5f, 0.5f) + StickerItem("content://sticker", 0.2f, 0.8f)
        assertFalse(overlay.isEmpty)
        assertEquals(1 to 1, overlay.strokes.size to overlay.texts.size)
        assertEquals(1, overlay.stickers.size)
    }

    @Test fun overlayIsImmutable() {
        val empty = Overlay()
        empty + TextItem("x", 0f, 0f)
        assertTrue(empty.isEmpty)
    }

    @Test fun defaultsAreRelativeToTheImage() {
        val text = TextItem("x", 0f, 0f)
        assertEquals("text height is 10 % of the image width, like before", 0.1f, text.size, 0f)
        assertEquals("stickers are 20 % of the image width, like before", 0.2f, StickerItem("u", 0f, 0f).width, 0f)
    }
}

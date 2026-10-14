package org.pixeldroid.media_editor.photoEdit.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayEditTest {

    private val overlay = Overlay() +
        TextItem("A", 0.1f, 0.2f, id = 1) + TextItem("B", 0.5f, 0.5f, id = 2) +
        StickerItem("s", 0.5f, 0.5f, width = 0.2f, aspect = 1f, id = 3)

    @Test fun nextIdIsAfterAllItems() {
        assertEquals(4L, overlay.nextId())
        assertEquals(1L, Overlay().nextId())
    }

    @Test fun movingOnlyMovesThatItem() {
        val moved = overlay.moveText(2, 0.8f, 0.9f)
        assertEquals(TextItem("B", 0.8f, 0.9f, id = 2), moved.texts[1])
        assertEquals(overlay.texts[0], moved.texts[0])
        assertEquals(0.3f to 0.4f, overlay.moveSticker(3, 0.3f, 0.4f).stickers[0].let { it.x to it.y })
    }

    @Test fun editingAndRemovingText() {
        assertEquals("C", overlay.editText(1, "C").texts[0].text)
        val removed = overlay.editText(1, "   ")
        assertEquals(listOf(2L), removed.texts.map { it.id })
    }

    @Test fun unknownIdChangesNothing() = assertEquals(overlay, overlay.moveText(99, 0f, 0f))

    // --- hit test, square image (aspect 1) ---

    @Test fun stickerHitIncludesAMargin() {
        assertEquals(3L, OverlayHitTest.stickerAt(overlay, RelativePoint(0.5f, 0.5f), 1f)?.id)
        assertEquals(3L, OverlayHitTest.stickerAt(overlay, RelativePoint(0.61f, 0.5f), 1f)?.id)   // edge 0.6 + margin
        assertNull(OverlayHitTest.stickerAt(overlay, RelativePoint(0.65f, 0.5f), 1f))
    }

    @Test fun stickerHeightFollowsAspectAndImageShape() {
        // Sticker twice as high as wide on a landscape image (height = 0.5 * width)
        val b = OverlayHitTest.stickerBounds(StickerItem("s", 0.5f, 0.5f, width = 0.2f, aspect = 2f), imageAspect = 0.5f)
        assertEquals(0.2f, b.right - b.left, 1e-6f)   // 0.2 of the image width
        assertEquals(0.8f, b.bottom - b.top, 1e-6f)   // 0.2 * 2 relative to width = 0.8 of the height
    }

    @Test fun textHitUsesTheMeasuredWidth() {
        val width = { _: TextItem -> 0.3f }
        // Text "A" at x 0.1..0.4, baseline 0.2, height 0.1
        assertEquals(1L, OverlayHitTest.textAt(overlay, RelativePoint(0.3f, 0.15f), 1f, width)?.id)
        assertNull(OverlayHitTest.textAt(overlay, RelativePoint(0.3f, 0.05f), 1f, width))
    }

    @Test fun topmostWins() {
        val stacked = Overlay() + StickerItem("a", 0.5f, 0.5f, id = 1) + StickerItem("b", 0.5f, 0.5f, id = 2)
        assertEquals(2L, OverlayHitTest.stickerAt(stacked, RelativePoint(0.5f, 0.5f), 1f)?.id)
        assertTrue(OverlayHitTest.stickerAt(Overlay(), RelativePoint(0.5f, 0.5f), 1f) == null)
    }
}

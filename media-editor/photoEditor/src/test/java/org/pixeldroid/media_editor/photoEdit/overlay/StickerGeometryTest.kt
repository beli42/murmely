package org.pixeldroid.media_editor.photoEdit.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StickerGeometryTest {

    private val eps = 1e-4f
    private fun p(x: Float, y: Float) = RelativePoint(x, y)

    /** 0.2 wide, twice as high as wide, in the middle */
    private val tall = StickerItem("s", 0.5f, 0.5f, width = 0.2f, aspect = 2f, id = 1)

    @Test fun turnedStickerIsHitWhereItIs() {
        // Square image: the sticker covers x 0.4..0.6, y 0.3..0.7
        assertTrue(StickerGeometry.contains(tall, p(0.5f, 0.68f), 1f))
        assertFalse(StickerGeometry.contains(tall, p(0.68f, 0.5f), 1f))
        // Turned 90°: now wide and low
        val turned = tall.copy(rotation = 90f)
        assertFalse(StickerGeometry.contains(turned, p(0.5f, 0.68f), 1f))
        assertTrue(StickerGeometry.contains(turned, p(0.68f, 0.5f), 1f))
        // The margin makes it easier to grab
        assertTrue(StickerGeometry.contains(tall, p(0.61f, 0.5f), 1f, margin = 0.02f))
    }

    @Test fun cornersFollowTheTurnOnAWideImage() {
        // Image twice as wide as high: 0.1 image widths are 0.2 of the height
        val square = StickerItem("s", 0.5f, 0.5f, width = 0.2f, id = 1)
        val handle = StickerGeometry.handle(square, 0.5f)
        assertEquals(0.6f, handle.x, eps)
        assertEquals(0.7f, handle.y, eps)
        // Turned 90° clockwise the bottom right corner is at the bottom left
        val turned = StickerGeometry.handle(square.copy(rotation = 90f), 0.5f)
        assertEquals(0.4f, turned.x, eps)
        assertEquals(0.7f, turned.y, eps)
    }

    @Test fun handleScalesAndTurnsAroundTheCentre() {
        val square = StickerItem("s", 0.5f, 0.5f, width = 0.2f, id = 1)
        // Handle from the corner (0.6, 0.6) to twice as far out: twice as big, same angle
        val bigger = StickerGeometry.scaleAndTurn(square, p(0.6f, 0.6f), p(0.7f, 0.7f), 1f)
        assertEquals(0.4f, bigger.width, eps)
        assertEquals(0f, bigger.rotation, eps)
        assertEquals("centre stays", 0.5f to 0.5f, bigger.x to bigger.y)
        // A quarter around the centre, same distance: turned 90° clockwise
        val turned = StickerGeometry.scaleAndTurn(square, p(0.6f, 0.6f), p(0.4f, 0.6f), 1f)
        assertEquals(0.2f, turned.width, eps)
        assertEquals(90f, turned.rotation, eps)
        // Limits
        assertEquals(StickerGeometry.MIN_WIDTH, StickerGeometry.scaleAndTurn(square, p(0.6f, 0.6f), p(0.501f, 0.501f), 1f).width, eps)
        assertEquals(StickerGeometry.MAX_WIDTH, StickerGeometry.scaleAndTurn(square, p(0.6f, 0.6f), p(5f, 5f), 1f).width, eps)
    }

    @Test fun pinchMovesScalesAndTurns() {
        val square = StickerItem("s", 0.5f, 0.5f, width = 0.2f, id = 1)
        // Fingers horizontal 0.2 apart around the centre, then vertical 0.4 apart, shifted right
        val result = StickerGeometry.pinch(
            square,
            p(0.4f, 0.5f), p(0.6f, 0.5f),
            p(0.6f, 0.3f), p(0.6f, 0.7f),
            1f,
        )
        assertEquals("moved with the middle", 0.6f, result.x, eps)
        assertEquals(0.5f, result.y, eps)
        assertEquals("twice the distance", 0.4f, result.width, eps)
        assertEquals("turned with the fingers", 90f, result.rotation, eps)
    }

    @Test fun moveStaysOnTheImage() {
        val moved = StickerGeometry.move(tall, p(0.5f, 0.5f), p(0.7f, 0.4f))
        assertEquals(0.7f, moved.x, eps)
        assertEquals(0.4f, moved.y, eps)
        assertEquals(1f, StickerGeometry.move(tall, p(0f, 0f), p(2f, 0f)).x, eps)
    }

    @Test fun anglesStayBetweenMinusAndPlus180() {
        assertEquals(-170f, StickerGeometry.normalise(190f), eps)
        assertEquals(170f, StickerGeometry.normalise(-190f), eps)
        assertEquals(180f, StickerGeometry.normalise(-180f), eps)
        assertEquals(0f, StickerGeometry.normalise(720f), eps)
    }

    @Test fun transformChangesOnlyThatSticker() {
        val other = tall.copy(id = 2)
        val overlay = Overlay(stickers = listOf(tall, other)).transformSticker(1, 0.3f, 0.4f, 0.5f, 45f)
        assertEquals(StickerItem("s", 0.3f, 0.4f, 0.5f, 2f, 1, 45f), overlay.stickers[0])
        assertEquals(other, overlay.stickers[1])
    }
}

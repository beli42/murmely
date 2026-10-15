package org.pixeldroid.media_editor.photoEdit

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pixeldroid.media_editor.photoEdit.crop.CropAspect
import org.pixeldroid.media_editor.photoEdit.crop.CropGeometry
import org.pixeldroid.media_editor.photoEdit.crop.CropHandle
import org.pixeldroid.media_editor.photoEdit.crop.CropRect
import org.pixeldroid.media_editor.photoEdit.crop.CropSettings

class CropGeometryTest {

    private val eps = 1e-5f

    private fun assertRect(expected: CropRect, actual: CropRect) {
        assertEquals("left", expected.left, actual.left, eps)
        assertEquals("top", expected.top, actual.top, eps)
        assertEquals("right", expected.right, actual.right, eps)
        assertEquals("bottom", expected.bottom, actual.bottom, eps)
    }

    @Test fun turningMovesTheRectWithTheImage() {
        // Top left quarter of the image
        val quarter = CropRect(0f, 0f, 0.5f, 0.5f)
        assertRect(CropRect(0.5f, 0f, 1f, 0.5f), quarter.rotated(1))     // now top right
        assertRect(CropRect(0.5f, 0.5f, 1f, 1f), quarter.rotated(2))     // bottom right
        assertRect(CropRect(0f, 0.5f, 0.5f, 1f), quarter.rotated(3))     // bottom left
        assertRect(quarter, quarter.rotated(4))
        assertRect(quarter, quarter.rotated(1).rotated(-1))
    }

    @Test fun aspectGivesTheLargestRectInsideTheImage() {
        // 4:3 image, square crop: full height, centred
        val square = CropGeometry.withAspect(CropRect.FULL, 1f, 4f / 3)
        assertRect(CropRect(0.125f, 0f, 0.875f, 1f), square)
        // 16:9 on the same image: full width
        val wide = CropGeometry.withAspect(CropRect.FULL, 16f / 9, 4f / 3)
        assertEquals(1f, wide.width, eps)
        assertEquals("pixel ratio", 16f / 9, wide.width * 4 / (wide.height * 3), 1e-4f)
        // Stays near the current crop, inside the image
        val shifted = CropGeometry.withAspect(CropRect(0f, 0f, 0.3f, 0.3f), 1f, 4f / 3)
        assertEquals(0f, shifted.left, eps)
        // Free: unchanged
        assertRect(CropRect(0.1f, 0.2f, 0.3f, 0.4f), CropGeometry.withAspect(CropRect(0.1f, 0.2f, 0.3f, 0.4f), null, 1f))
    }

    @Test fun freeDragStaysInsideAndAboveTheMinimum() {
        val r = CropRect(0.2f, 0.2f, 0.8f, 0.8f)
        assertRect(CropRect(0f, 0.2f, 0.8f, 0.8f), CropGeometry.drag(r, CropHandle.LEFT, -0.5f, 0f, null, 1f))
        val tiny = CropGeometry.drag(r, CropHandle.BOTTOM_RIGHT, -1f, -1f, null, 1f)
        assertEquals(CropGeometry.MIN_SIZE, tiny.width, eps)
        assertEquals(CropGeometry.MIN_SIZE, tiny.height, eps)
        // Moving keeps the size and stops at the border
        val moved = CropGeometry.drag(r, CropHandle.MOVE, 0.5f, -0.1f, null, 1f)
        assertRect(CropRect(0.4f, 0.1f, 1f, 0.7f), moved)
    }

    @Test fun cornerDragKeepsTheShape() {
        // Square image, 2:1 crop
        val r = CropRect(0.2f, 0.4f, 0.6f, 0.6f)
        val bigger = CropGeometry.drag(r, CropHandle.BOTTOM_RIGHT, 0.2f, 0f, 2f, 1f)
        assertEquals(2f, bigger.width / bigger.height, 1e-4f)
        assertEquals("top left corner fixed", 0.2f, bigger.left, eps)
        assertEquals(0.4f, bigger.top, eps)
        assertEquals(0.6f, bigger.width, eps)
        // Can't leave the image
        val huge = CropGeometry.drag(r, CropHandle.BOTTOM_RIGHT, 5f, 5f, 2f, 1f)
        assertTrue(huge.right <= 1f + eps && huge.bottom <= 1f + eps)
        assertEquals(2f, huge.width / huge.height, 1e-4f)
        // Shrinking with a horizontal drag works too
        val smaller = CropGeometry.drag(r, CropHandle.TOP_LEFT, 0.2f, 0f, 2f, 1f)
        assertEquals(0.2f, smaller.width, eps)
        assertEquals("bottom right corner fixed", 0.6f, smaller.right, eps)
        assertEquals(0.6f, smaller.bottom, eps)
        // Edges do nothing while the shape is fixed
        assertRect(r, CropGeometry.drag(r, CropHandle.LEFT, 0.1f, 0f, 2f, 1f))
    }

    @Test fun sourcePixelsUndoTheTurns() {
        // 400 x 200 image turned once clockwise (now 200 x 400): the left edge became the top,
        // so the top half of the turned image is the left half of the original
        val settings = CropSettings(CropRect(0f, 0f, 1f, 0.5f), quarterTurns = 1)
        assertArrayEquals(intArrayOf(0, 0, 200, 200), CropGeometry.sourcePixels(settings, 400, 200))
        val other = CropSettings(CropRect(0f, 0.5f, 1f, 1f), quarterTurns = 1)
        assertArrayEquals(intArrayOf(200, 0, 200, 200), CropGeometry.sourcePixels(other, 400, 200))
        assertArrayEquals(intArrayOf(0, 0, 400, 200), CropGeometry.sourcePixels(CropSettings(), 400, 200))
    }

    @Test fun turningTurnsTheCropToo() {
        val s = CropGeometry.turn(CropSettings(CropRect(0f, 0f, 0.5f, 0.5f)), 1)
        assertEquals(1, s.quarterTurns)
        assertRect(CropRect(0.5f, 0f, 1f, 0.5f), s.rect)
        assertEquals(3, CropGeometry.turn(CropSettings(), -1).quarterTurns)
        assertTrue(CropGeometry.turn(CropGeometry.turn(CropSettings(), 1), -1).isOriginal)
        assertEquals(0.5f, CropGeometry.turnedAspect(400, 200, 1), eps)
        assertEquals(4f / 3, CropAspect.ORIGINAL.pixelRatio(4f / 3)!!, eps)
    }

    @Test fun turningKeepsAFixedShape() {
        // 400 x 300 image, 4:3 crop of the whole image; turned it is 300 x 400, the crop stays 4:3
        val s = CropGeometry.setAspect(CropSettings(), CropAspect.LANDSCAPE_4_3, 400, 300)
        assertRect(CropRect.FULL, s.rect)
        val turned = CropGeometry.turnBy(s, 1, 400, 300)
        assertEquals("pixel ratio", 4f / 3, turned.rect.width * 300 / (turned.rect.height * 400), 1e-4f)
        assertEquals("portrait image: full width", 1f, turned.rect.width, eps)
        // Free crops just turn
        val free = CropGeometry.turnBy(CropSettings(CropRect(0f, 0f, 0.5f, 0.5f)), 1, 400, 300)
        assertRect(CropRect(0.5f, 0f, 1f, 0.5f), free.rect)
    }

    @Test fun handlesAreFoundCornersFirst() {
        val r = CropRect(0.2f, 0.2f, 0.8f, 0.8f)
        fun at(x: Float, y: Float, fixed: Boolean = false) = CropGeometry.handleAt(r, x, y, 0.05f, 0.05f, fixed)
        assertEquals(CropHandle.TOP_LEFT, at(0.21f, 0.18f))
        assertEquals(CropHandle.BOTTOM_RIGHT, at(0.82f, 0.79f))
        assertEquals(CropHandle.LEFT, at(0.2f, 0.5f))
        assertEquals(CropHandle.BOTTOM, at(0.5f, 0.83f))
        assertEquals(CropHandle.MOVE, at(0.5f, 0.5f))
        assertEquals("edge with a fixed shape: move", CropHandle.MOVE, at(0.23f, 0.5f, fixed = true))
        assertEquals(null, at(0.05f, 0.5f))
        // Tiny crop: the nearest corner wins
        val tiny = CropRect(0.5f, 0.5f, 0.52f, 0.52f)
        assertEquals(CropHandle.BOTTOM_RIGHT, CropGeometry.handleAt(tiny, 0.53f, 0.53f, 0.05f, 0.05f, false))
        assertEquals(CropHandle.TOP_LEFT, CropGeometry.handleAt(tiny, 0.49f, 0.49f, 0.05f, 0.05f, false))
    }
}

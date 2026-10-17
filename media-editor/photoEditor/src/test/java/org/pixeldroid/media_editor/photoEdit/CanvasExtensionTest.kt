package org.pixeldroid.media_editor.photoEdit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pixeldroid.media_editor.photoEdit.canvas.Bounds
import org.pixeldroid.media_editor.photoEdit.canvas.CanvasExtension
import org.pixeldroid.media_editor.photoEdit.canvas.Side

class CanvasExtensionTest {

    @Test fun addedOnTheChosenSidesRelativeToTheShorterSide() {
        // 25 % of 800 = 200 px below a 1200 x 800 photo, for a caption
        val below = CanvasExtension(size = 0.25f, sides = setOf(Side.BOTTOM))
        assertEquals(1200 to 1000, below.canvasSize(1200, 800))
        assertEquals(0 to 0, below.photoOffset(1200, 800))
        // All around
        val around = CanvasExtension(size = 0.1f)
        assertEquals(1360 to 960, around.canvasSize(1200, 800))
        assertEquals(80 to 80, around.photoOffset(1200, 800))
    }

    @Test fun boundsAreRelativeToThePhoto() {
        val below = CanvasExtension(size = 0.25f, sides = setOf(Side.BOTTOM))
        assertEquals(Bounds(0f, 0f, 1f, 1.25f), below.bounds(1200, 800))
        val left = CanvasExtension(size = 0.5f, sides = setOf(Side.LEFT))
        val b = left.bounds(1000, 1000)
        assertEquals(-0.5f, b.left, 1e-6f)
        assertEquals(1.5f, b.width, 1e-6f)
    }

    @Test fun nothingAddedIsNone() {
        assertTrue(CanvasExtension().isNone)
        assertTrue(CanvasExtension(size = 0.2f, sides = emptySet()).isNone)
        assertFalse(CanvasExtension(size = 0.2f).isNone)
        assertEquals(1200 to 800, CanvasExtension(size = 0.2f, sides = emptySet()).canvasSize(1200, 800))
        assertEquals(0xFFFFFFFF.toInt(), CanvasExtension().color)
    }
}

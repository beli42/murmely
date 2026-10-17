package org.pixeldroid.media_editor.photoEdit

import org.junit.Assert.assertEquals
import org.junit.Test
import org.pixeldroid.media_editor.photoEdit.panel.Corner
import org.pixeldroid.media_editor.photoEdit.panel.FloatingPosition

class FloatingPositionTest {

    @Test fun tapsGoRoundClockwise() {
        assertEquals(Corner.BOTTOM_RIGHT, Corner.TOP_RIGHT.next)
        assertEquals(Corner.BOTTOM_LEFT, Corner.BOTTOM_RIGHT.next)
        assertEquals(Corner.TOP_LEFT, Corner.BOTTOM_LEFT.next)
        assertEquals(Corner.TOP_RIGHT, Corner.TOP_LEFT.next)
    }

    @Test fun cornersAreInsideWithAMargin() {
        // 200 x 50 control in a 1000 x 800 preview, 10 from the edges
        fun at(corner: Corner) = FloatingPosition.inCorner(corner, 1000, 800, 200, 50, 10, bottomRightExtra = 60)
        assertEquals(790 to 10, at(Corner.TOP_RIGHT))
        assertEquals("above the button there", 790 to 680, at(Corner.BOTTOM_RIGHT))
        assertEquals(10 to 740, at(Corner.BOTTOM_LEFT))
        assertEquals(10 to 10, at(Corner.TOP_LEFT))
    }

    @Test fun draggedStaysFullyInside() {
        assertEquals(800 to 750, FloatingPosition.clamp(950, 900, 1000, 800, 200, 50))
        assertEquals(0 to 0, FloatingPosition.clamp(-30, -5, 1000, 800, 200, 50))
        assertEquals(300 to 400, FloatingPosition.clamp(300, 400, 1000, 800, 200, 50))
        // Container smaller than the control: at its start
        assertEquals(0 to 0, FloatingPosition.clamp(50, 50, 100, 30, 200, 50))
    }
}

package org.pixeldroid.media_editor.photoEdit

import org.junit.Assert.assertEquals
import org.junit.Test
import org.pixeldroid.media_editor.photoEdit.collage.CollageImages

class CollageImagesTest {

    @Test fun pickedImagesFillTheFreeAreasInOrder() {
        // 4 areas, area 2 already has an image
        val filled = CollageImages.fill(mapOf(2 to "b"), 4, listOf("x", "y", "z"))
        assertEquals(mapOf(1 to "x", 2 to "b", 3 to "y", 4 to "z"), filled)
        assertEquals("only as many as there are free areas", 3, CollageImages.pickLimit(mapOf(2 to "b"), 4))
    }

    @Test fun moreImagesThanFreeAreasAreLeftOut() {
        assertEquals(mapOf(1 to "a", 2 to "x"), CollageImages.fill(mapOf(1 to "a"), 2, listOf("x", "y")))
    }

    @Test fun withNoFreeAreaThePickedOnesReplaceFromTheFirst() {
        val full = mapOf(1 to "a", 2 to "b", 3 to "c")
        assertEquals(3, CollageImages.pickLimit(full, 3))
        assertEquals(mapOf(1 to "x", 2 to "y", 3 to "c"), CollageImages.fill(full, 3, listOf("x", "y")))
    }

    @Test fun movingSwapsOrMoves() {
        val images = mapOf(1 to "a", 2 to "b")
        assertEquals("swapped", mapOf(1 to "b", 2 to "a"), CollageImages.move(images, 1, 2))
        assertEquals("moved into an empty area", mapOf(2 to "b", 3 to "a"), CollageImages.move(images, 1, 3))
        assertEquals("from an empty area: nothing", images, CollageImages.move(images, 3, 1))
        assertEquals("onto itself: nothing", images, CollageImages.move(images, 1, 1))
    }

    @Test fun areasFromTheTopLeftClockwise() {
        // Areas by where their middles are
        val grid = mapOf(1 to (0.75f to 0.75f), 2 to (0.25f to 0.25f), 3 to (0.25f to 0.75f), 4 to (0.75f to 0.25f))
        assertEquals("top left, top right, bottom right, bottom left", listOf(2, 4, 1, 3), CollageImages.clockwise(4) { grid.getValue(it) })
        val sideBySide = mapOf(1 to (0.75f to 0.5f), 2 to (0.25f to 0.5f))
        assertEquals("left, then right", listOf(2, 1), CollageImages.clockwise(2) { sideBySide.getValue(it) })
        val aboveBelow = mapOf(1 to (0.5f to 0.75f), 2 to (0.5f to 0.25f))
        assertEquals("top, then bottom", listOf(2, 1), CollageImages.clockwise(2) { aboveBelow.getValue(it) })
        val bigAndSmall = mapOf(1 to (0.5f to 0.5f), 2 to (0.2f to 0.2f))
        assertEquals("the big one (middle) first, then the small one", listOf(1, 2), CollageImages.clockwise(2) { bigAndSmall.getValue(it) })
    }

    @Test fun picksFillInThatOrder() {
        // Order 2, 4, 1, 3; area 4 already has one
        assertEquals(mapOf(2 to "x", 4 to "b", 1 to "y", 3 to "z"), CollageImages.fill(mapOf(4 to "b"), 4, listOf("x", "y", "z"), listOf(2, 4, 1, 3)))
    }

    @Test fun theAppsLayoutsFillFromTheTopLeftClockwise() {
        for (layout in 1..4) {
            val order = CollageGeometry.fillOrder(layout)
            val first = CollageGeometry.cell(layout, order.first(), 1000, 1000)
            // The first area contains the top left corner (or is the big one filling everything)
            assertEquals("layout $layout starts at the top left", 0 to 0, first.left to first.top)
            assertEquals("every area once", (1..CollageGeometry.imageCount(layout)).toSet(), order.toSet())
        }
    }
}

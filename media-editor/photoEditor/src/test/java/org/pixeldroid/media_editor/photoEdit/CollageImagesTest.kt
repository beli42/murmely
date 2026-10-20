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


    @Test fun picksFillInThatOrder() {
        // Order 2, 4, 1, 3; area 4 already has one
        assertEquals(mapOf(2 to "x", 4 to "b", 1 to "y", 3 to "z"), CollageImages.fill(mapOf(4 to "b"), 4, listOf("x", "y", "z"), listOf(2, 4, 1, 3)))
    }

}

package org.pixeldroid.media_editor.photoEdit

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorLayoutTest {

    // A typical phone in portrait: 400 x 860
    private fun height(aspect: Float?) = EditorLayout.previewHeight(400f, 860f, aspect)

    @Test fun asHighAsThePhotoNeeds() {
        // Square photo: 400 high (between 430 and 602): the least, not the most
        assertEquals("square: the least", 430f, height(1f), 0.01f)
        // 3:4 portrait photo: 533
        assertEquals(533.33f, height(4f / 3f), 0.01f)
    }

    @Test fun withinTheLimits() {
        // Wide photo (16:9): would be 225, at least half the screen
        assertEquals(430f, height(9f / 16f), 0.01f)
        // Tall photo (9:16): would be 711, at most 70 %
        assertEquals(602f, height(16f / 9f), 0.01f)
    }

    @Test fun unknownAspectTakesTheMost() {
        assertEquals(602f, height(null), 0.01f)
        assertEquals(602f, height(0f), 0.01f)
    }
}

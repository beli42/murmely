package org.pixeldroid.media_editor.photoEdit

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorLayoutTest {

    @Test fun tilesAimForAboutTwoAndAHalfAcross() {
        assertEquals(153.85f, EditorLayout.tileTarget(400f), 0.01f)
        assertEquals("narrow screen: at least 72", 72f, EditorLayout.tileTarget(150f), 0f)
        assertEquals("tablet: at most 160", 160f, EditorLayout.tileTarget(800f), 0f)
    }

    @Test fun photoAtLeast55Percent() {
        // A phone 400 x 860: 55 % = 473
        // Wide photo (16:9) only needs 225, but gets 55 %
        assertEquals(473f, EditorLayout.previewHeight(400f, 860f, 9f / 16f), 0.01f)
        // Large text and large display size: still 55 % (the tiles get smaller instead)
        assertEquals(473f, EditorLayout.previewHeight(400f, 860f, 16f / 9f, fontScale = 2f), 0.01f)
    }

    @Test fun tallPhotosLeaveRoomForLargeTiles() {
        // Tall photo (9:16) would take 70 % = 602; the tools need 196 + 154 for a large tile,
        // so the photo gets 510 (between 55 and 70 %)
        assertEquals(510.15f, EditorLayout.previewHeight(400f, 860f, 16f / 9f), 0.01f)
    }

    @Test fun unknownAspectAsIfTall() {
        assertEquals(EditorLayout.previewHeight(400f, 860f, 16f / 9f), EditorLayout.previewHeight(400f, 860f, null), 0.01f)
    }

    @Test fun landscapePhotoAsWideAsItNeeds() {
        // 4:3 landscape photo on an 800 x 360 screen: 480 wide at full height, less than 70 %
        assertEquals(480f, EditorLayout.landscapePhotoWidth(800f, 360f, 0.75f, 0.7f), 0.01f)
        // Very wide photo: at most the share
        assertEquals(560f, EditorLayout.landscapePhotoWidth(800f, 360f, 0.25f, 0.7f), 0.01f)
        // Unknown shape: the share
        assertEquals(560f, EditorLayout.landscapePhotoWidth(800f, 360f, null, 0.7f), 0.01f)
    }
}

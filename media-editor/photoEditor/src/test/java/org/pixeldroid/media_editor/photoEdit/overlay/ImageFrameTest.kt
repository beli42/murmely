package org.pixeldroid.media_editor.photoEdit.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageFrameTest {

    @Test fun landscapeImageInPortraitViewHasBarsAboveAndBelow() {
        val frame = ImageFrame(imageWidth = 4000, imageHeight = 3000, viewWidth = 1000, viewHeight = 2000)
        assertEquals(1000, frame.shownWidth)
        assertEquals(750, frame.shownHeight)
        assertEquals(0, frame.sideBar)
        assertEquals(625, frame.topBar)
    }

    @Test fun portraitImageInLandscapeViewHasBarsOnTheSides() {
        val frame = ImageFrame(1080, 1920, 2000, 1000)
        assertEquals(563, frame.shownWidth)   // 1000 * 1080/1920 = 562.5
        assertEquals(1000, frame.shownHeight)
        assertEquals(718, frame.sideBar)
        assertEquals(0, frame.topBar)
    }

    @Test fun sameRatioFillsTheView() {
        val frame = ImageFrame(400, 300, 800, 600)
        assertEquals(800 to 600, frame.shownWidth to frame.shownHeight)
        assertEquals(0 to 0, frame.sideBar to frame.topBar)
    }

    @Test fun corners() {
        val frame = ImageFrame(4000, 3000, 1000, 2000)
        assertEquals(RelativePoint(0f, 0f), frame.toImage(0f, 625f))
        assertEquals(RelativePoint(1f, 1f), frame.toImage(1000f, 1375f))
        assertEquals(500f to 1000f, frame.toView(RelativePoint(0.5f, 0.5f)))
    }

    @Test fun roundTrip() {
        val frame = ImageFrame(1080, 1920, 2000, 1000)
        for (p in listOf(RelativePoint(0.1f, 0.2f), RelativePoint(0.73f, 0.5f), RelativePoint(1f, 0f))) {
            val (x, y) = frame.toView(p)
            val back = frame.toImage(x, y)
            assertEquals(p.x, back.x, 1e-5f)
            assertEquals(p.y, back.y, 1e-5f)
        }
    }

    @Test fun barsAreOutsideTheImage() {
        val p = ImageFrame(4000, 3000, 1000, 2000).toImage(500f, 100f)
        assertEquals(true, p.y < 0f)
    }
}

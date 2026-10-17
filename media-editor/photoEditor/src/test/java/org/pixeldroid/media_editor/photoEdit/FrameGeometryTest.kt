package org.pixeldroid.media_editor.photoEdit

import org.junit.Assert.assertEquals
import org.junit.Test
import org.pixeldroid.media_editor.photoEdit.frame.FrameGeometry
import org.pixeldroid.media_editor.photoEdit.frame.FrameMargins
import org.pixeldroid.media_editor.photoEdit.frame.FramePlacement
import org.pixeldroid.media_editor.photoEdit.frame.FrameSettings
import org.pixeldroid.media_editor.photoEdit.frame.FrameStyle
import org.pixeldroid.media_editor.photoEdit.frame.PixelRect

class FrameGeometryTest {

    private val outside = FrameSettings(FrameStyle.SOLID, FramePlacement.OUTSIDE, size = 0.05f)
    private val inside = outside.copy(placement = FramePlacement.INSIDE)

    @Test fun widthIsRelativeToTheShorterSide() {
        // 4000 x 3000: 5 % of 3000 = 150 px on every side
        assertEquals(FrameMargins(150, 150, 150, 150), FrameGeometry.margins(outside, 4000, 3000))
        // Same look at a quarter of the size
        assertEquals(FrameMargins(38, 38, 38, 38), FrameGeometry.margins(outside, 1000, 750))
        // Never thinner than a pixel
        assertEquals(FrameMargins(1, 1, 1, 1), FrameGeometry.margins(outside.copy(size = 0.01f), 40, 30))
    }

    @Test fun polaroidAndFilmHaveTheirOwnShape() {
        assertEquals(FrameMargins(100, 100, 100, 350), FrameGeometry.margins(FrameSettings(FrameStyle.POLAROID, size = 0.1f), 2000, 1000))
        assertEquals(FrameMargins(35, 140, 35, 140), FrameGeometry.margins(FrameSettings(FrameStyle.FILM, size = 0.1f), 2000, 1000))
    }

    @Test fun outsideMakesTheImageLarger() {
        assertEquals(4300 to 3300, FrameGeometry.outputSize(outside, 4000, 3000))
        assertEquals(4000 to 3000, FrameGeometry.outputSize(inside, 4000, 3000))
        assertEquals(4000 to 3000, FrameGeometry.outputSize(null, 4000, 3000))
    }

    @Test fun windowIsWhereThePhotoShows() {
        // Outside: all of the photo, behind the frame
        assertEquals(PixelRect(150, 150, 4000, 3000), FrameGeometry.window(outside, 4000, 3000))
        // Inside: the photo less the frame
        assertEquals(PixelRect(150, 150, 3700, 2700), FrameGeometry.window(inside, 4000, 3000))
    }

    @Test fun onScreenThePhotoShrinksForAFrameOutside() {
        // 1000 x 1000 photo, 10 % frame outside: 1200 x 1200 result in a 600 x 600 view
        val layout = FrameGeometry.layoutOnScreen(outside.copy(size = 0.1f), 1000, 1000, 600, 600)
        assertEquals(0.5f, layout.scale, 1e-6f)
        assertEquals(PixelRect(0, 0, 600, 600), layout.output)
        assertEquals(PixelRect(50, 50, 500, 500), layout.photo)
        // Without a frame the photo fills the view as before
        val plain = FrameGeometry.layoutOnScreen(null, 1000, 1000, 600, 600)
        assertEquals(PixelRect(0, 0, 600, 600), plain.photo)
        assertEquals(plain.output, plain.photo)
        // Inside: same as without a frame
        assertEquals(plain, FrameGeometry.layoutOnScreen(inside, 1000, 1000, 600, 600))
    }

    @Test fun newFramesHaveTheirColour() {
        assertEquals(0f, FrameSettings.of(FrameStyle.FILM).lightness, 0f)
        assertEquals(1f, FrameSettings.of(FrameStyle.POLAROID).lightness, 0f)
        assertEquals(0xFFFFFFFF.toInt(), FrameSettings.of(FrameStyle.SOLID).color)
        assertEquals(0xFF000000.toInt(), FrameSettings.of(FrameStyle.FILM).color)
    }
}

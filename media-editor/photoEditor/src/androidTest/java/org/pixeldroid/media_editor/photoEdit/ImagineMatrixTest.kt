package org.pixeldroid.media_editor.photoEdit

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineMatrix

/** Fit and fill scale the quad so that the image keeps its proportions */
@RunWith(AndroidJUnit4::class)
class ImagineMatrixTest {

    private fun scale(m: ImagineMatrix) = m.data[0] to m.data[5]

    @Test fun fillCoversTheSquare() {
        // Portrait photo (1:2) in a square: as wide as the square, twice as high (cut top and bottom)
        assertEquals(1f to 2f, scale(ImagineMatrix.ofAspectFill(0.5f, 1f)))
        // Landscape (2:1): twice as wide, as high as the square
        assertEquals(2f to 1f, scale(ImagineMatrix.ofAspectFill(2f, 1f)))
        assertEquals(1f to 1f, scale(ImagineMatrix.ofAspectFill(1f, 1f)))
    }

    @Test fun fitStaysInside() {
        // The same photos fitted leave bands: narrower (portrait) or lower (landscape)
        assertEquals(0.5f to 1f, scale(ImagineMatrix.ofAspectFit(0.5f, 1f)))
        assertEquals(1f to 0.5f, scale(ImagineMatrix.ofAspectFit(2f, 1f)))
    }
}

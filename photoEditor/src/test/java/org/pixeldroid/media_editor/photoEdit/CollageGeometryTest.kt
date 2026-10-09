package org.pixeldroid.media_editor.photoEdit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollageGeometryTest {

    private val size = 2000

    private fun cells(layout: Int) =
        (1..CollageGeometry.imageCount(layout)).map { CollageGeometry.cell(layout, it, size, size) }

    @Test fun cellsOfTheLayouts() {
        assertEquals(listOf(Box(0, 0, 2000, 2000), Box(0, 0, 800, 800)), cells(1))
        assertEquals(listOf(Box(0, 0, 1000, 2000), Box(1000, 0, 2000, 2000)), cells(2))
        assertEquals(listOf(Box(0, 0, 2000, 1000), Box(0, 1000, 2000, 2000)), cells(3))
        assertEquals(
            listOf(Box(0, 0, 1000, 1000), Box(1000, 0, 2000, 1000), Box(0, 1000, 1000, 2000), Box(1000, 1000, 2000, 2000)),
            cells(4)
        )
    }

    @Test fun layouts2to4CoverTheWholeCollageWithoutOverlap() {
        for (layout in 2..4) {
            val c = cells(layout)
            assertEquals("area of layout $layout", size * size, c.sumOf { it.width * it.height })
            for (i in c.indices) for (j in i + 1 until c.size) {
                val overlap = maxOf(0, minOf(c[i].right, c[j].right) - maxOf(c[i].left, c[j].left)) *
                    maxOf(0, minOf(c[i].bottom, c[j].bottom) - maxOf(c[i].top, c[j].top))
                assertEquals("cells $i and $j of layout $layout overlap", 0, overlap)
            }
        }
    }

    @Test fun oddSizesLeaveNoGaps() {
        val c = (1..4).map { CollageGeometry.cell(4, it, 1001, 999) }
        // Columns meet exactly and the grid starts at 0
        assertEquals(c[0].right, c[1].left)
        assertEquals(c[0].bottom, c[2].top)
        assertTrue(c.all { it.left >= 0 && it.top >= 0 && it.right <= 1001 && it.bottom <= 999 })
    }

    @Test fun fitKeepsAspectRatioAndCenters() {
        val cell = Box(0, 0, 1000, 2000)                       // portrait cell
        val (w, h) = CollageGeometry.loadedSize(cell, 1600, 900, CollageScaleMode.FIT) // landscape image
        assertEquals(1000, w)
        assertEquals(563, h)                                   // 900 * 1000/1600 = 562.5
        val p = CollageGeometry.placement(cell, w, h, CollageScaleMode.FIT)
        assertEquals(0f, p.left, 0f)
        assertEquals((2000 - 563) / 2f, p.top, 0.01f)
        assertEquals(p.top, 2000 - p.bottom, 0.01f)            // same bar top and bottom
    }

    @Test fun cropFillsExactly() {
        val cell = Box(1000, 0, 2000, 2000)
        assertEquals(1000 to 2000, CollageGeometry.loadedSize(cell, 1600, 900, CollageScaleMode.CROP))
        val p = CollageGeometry.placement(cell, 1000, 2000, CollageScaleMode.CROP)
        assertEquals(Placement(1000f, 0f, 2000f, 2000f), p)
    }

    @Test fun stretchAlwaysFillsTheCell() {
        val cell = Box(0, 1000, 2000, 2000)
        // Whatever size was loaded, it is drawn into the whole cell
        for ((w, h) in listOf(1778 to 1000, 563 to 1000, 2000 to 1000)) {
            assertEquals(Placement(0f, 1000f, 2000f, 2000f), CollageGeometry.placement(cell, w, h, CollageScaleMode.STRETCH))
        }
    }

    @Test fun placementStaysInsideTheCell() {
        val cell = Box(0, 0, 800, 800)
        for (mode in CollageScaleMode.entries) for ((iw, ih) in listOf(4000 to 3000, 1080 to 1920, 500 to 500, 10 to 4000)) {
            val (w, h) = CollageGeometry.loadedSize(cell, iw, ih, mode)
            val p = CollageGeometry.placement(cell, w, h, mode)
            assertTrue("$mode $iw x $ih: $p", p.left >= -0.5f && p.top >= -0.5f && p.right <= 800.5f && p.bottom <= 800.5f)
        }
    }

    @Test fun scaleModeCycle() {
        assertEquals(CollageScaleMode.FIT, CollageScaleMode.STRETCH.next())
        assertEquals(CollageScaleMode.CROP, CollageScaleMode.FIT.next())
        assertEquals(CollageScaleMode.STRETCH, CollageScaleMode.CROP.next())
    }
}

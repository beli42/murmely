package org.pixeldroid.media_editor.photoEdit

import org.junit.Assert.assertEquals
import org.junit.Test

class SliderValueFormatTest {

    @Test fun percentOfSymmetricRange() {
        assertEquals(0, SliderValueFormat.percent(0f, -1f, 1f))
        assertEquals(100, SliderValueFormat.percent(1f, -1f, 1f))
        assertEquals(-100, SliderValueFormat.percent(-1f, -1f, 1f))
        assertEquals(35, SliderValueFormat.percent(0.35f, -1f, 1f))
    }

    @Test fun tinyBrightnessRangeStillShowsPercent() {
        assertEquals(100, SliderValueFormat.percent(0.004f, -0.004f, 0.004f))
        assertEquals(-50, SliderValueFormat.percent(-0.002f, -0.004f, 0.004f))
    }

    @Test fun oneSidedRange() = assertEquals(50, SliderValueFormat.percent(0.5f, 0f, 1f))

    @Test fun emptyRange() = assertEquals(0, SliderValueFormat.percent(0f, 0f, 0f))

    @Test fun labels() {
        assertEquals("+35", SliderValueFormat.label(35))
        assertEquals("\u221220", SliderValueFormat.label(-20))   // real minus sign
        assertEquals("0", SliderValueFormat.label(0))
    }
}

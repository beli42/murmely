package org.pixeldroid.media_editor.photoEdit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdjustmentsTest {

    @Test fun neutralByDefault() {
        val sliders = Sliders()
        assertTrue(sliders.isNeutral)
        Adjustment.entries.forEach { assertEquals(it.name, 0f, sliders[it], 0f) }
    }

    @Test fun settingAValue() {
        val sliders = Sliders().with(Adjustment.CONTRAST, 0.5f)
        assertEquals(0.5f, sliders[Adjustment.CONTRAST], 0f)
        assertEquals("others unchanged", 0f, sliders[Adjustment.BRIGHTNESS], 0f)
        assertFalse(sliders.isNeutral)
    }

    @Test fun valuesAreClampedToTheRange() {
        assertEquals(0.004f, Sliders().with(Adjustment.BRIGHTNESS, 1f)[Adjustment.BRIGHTNESS], 0f)
        assertEquals(0f, Sliders().with(Adjustment.GRAIN, -1f)[Adjustment.GRAIN], 0f)
        assertEquals(-0.9f, Sliders().with(Adjustment.CONTRAST, -5f)[Adjustment.CONTRAST], 0f)
    }

    /** "No edits" and the undo history compare sliders: back at 0 must equal untouched */
    @Test fun backToZeroEqualsUntouched() {
        val changedAndBack = Sliders().with(Adjustment.WARMTH, 0.3f).with(Adjustment.WARMTH, 0f)
        assertEquals(Sliders(), changedAndBack)
        assertTrue(changedAndBack.isNeutral)
        assertNotEquals(Sliders(), Sliders().with(Adjustment.WARMTH, 0.3f))
    }

    @Test fun everyRangeContainsZero() =
        Adjustment.entries.forEach { assertTrue(it.name, it.min <= 0f && it.max > 0f) }

    @Test fun thirteenAdjustmentsInPanelOrder() {
        assertEquals(13, Adjustment.entries.size)
        assertEquals(Adjustment.EXPOSURE, Adjustment.entries.first())
        assertEquals(Adjustment.GRAIN, Adjustment.entries.last())
    }
}

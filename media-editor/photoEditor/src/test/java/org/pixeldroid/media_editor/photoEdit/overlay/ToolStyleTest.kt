package org.pixeldroid.media_editor.photoEdit.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolStyleTest {

    private fun hex(color: Int) = "#%08X".format(color)

    @Test fun defaultIsTheRedFromBefore() = assertEquals(hex(OverlayDefaults.COLOR), hex(ToolStyle().color))

    @Test fun primaryColours() {
        assertEquals("#FFFF0000", hex(hslColor(0f, 1f, 0.5f)))
        assertEquals("#FF00FF00", hex(hslColor(120f, 1f, 0.5f)))
        assertEquals("#FF0000FF", hex(hslColor(240f, 1f, 0.5f)))
        assertEquals("#FFFFFF00", hex(hslColor(60f, 1f, 0.5f)))
    }

    @Test fun lightnessGoesFromBlackToWhite() {
        assertEquals("#FF000000", hex(ToolStyle(hue = 200f, lightness = 0f).color))
        assertEquals("#FFFFFFFF", hex(ToolStyle(hue = 200f, lightness = 1f).color))
        assertEquals("#FF800000", hex(ToolStyle(hue = 0f, lightness = 0.25f).color))   // dark red
    }

    @Test fun hueWrapsAround() = assertEquals(hex(hslColor(0f, 1f, 0.5f)), hex(hslColor(360f, 1f, 0.5f)))

    @Test fun alwaysOpaque() {
        for (h in 0..360 step 30) for (l in listOf(0f, 0.3f, 0.5f, 0.8f, 1f)) {
            assertEquals(0xFF, (ToolStyle(h.toFloat(), l).color ushr 24) and 0xFF)
        }
    }

    @Test fun colourBackToSliders() {
        for (hue in listOf(0f, 45f, 120f, 200f, 300f)) for (lightness in listOf(0.2f, 0.5f, 0.8f)) {
            val style = ToolStyle(hue = 10f).withColor(hslColor(hue, 1f, lightness))
            assertEquals("hue of $hue/$lightness", hue, style.hue, 1.5f)
            assertEquals("lightness of $hue/$lightness", lightness, style.lightness, 0.01f)
        }
        // Black and white: the hue stays as it was
        assertEquals(77f, ToolStyle(hue = 77f).withColor(0xFF000000.toInt()).hue, 0f)
        assertEquals(1f, ToolStyle(hue = 77f).withColor(0xFFFFFFFF.toInt()).lightness, 0.001f)
    }
}

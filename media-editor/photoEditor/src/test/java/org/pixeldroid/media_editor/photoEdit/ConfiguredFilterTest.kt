package org.pixeldroid.media_editor.photoEdit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ConfiguredFilter
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.FilterSettings
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.base
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.isAdjustable
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.settings
import org.pixeldroid.media_editor.photoEdit.imagine.layers.ElsaLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.MirrorHorizontalLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.SwirlLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.TinyPlanetLayer

/** What a filter's settings turn into for the shader */
class ConfiguredFilterTest {

    private val half = FilterSettings(strength = 0.5f, centerX = 0.2f, centerY = 0.8f)

    @Test fun colourFilterBlendsWithItsStrength() {
        val elsa = ConfiguredFilter(ElsaLayer(), half)
        assertEquals("mixed with the original", 0.5f, elsa.intensity, 0f)
        assertEquals("shader unchanged", 1f, elsa.shaderStrength, 0f)
        assertEquals("no centre", 0.5f to 0.5f, elsa.centerX to elsa.centerY)
    }

    @Test fun geometricFilterScalesItsEffect() {
        val swirl = ConfiguredFilter(SwirlLayer(), half)
        assertEquals("no blending: no ghost image", 1f, swirl.intensity, 0f)
        assertEquals(0.5f, swirl.shaderStrength, 0f)
        assertEquals(0.2f to 0.8f, swirl.centerX to swirl.centerY)
    }

    @Test fun mirrorHasOnlyItsLine() {
        val mirror = ConfiguredFilter(MirrorHorizontalLayer(), half)
        assertEquals(1f, mirror.intensity, 0f)
        assertEquals(1f, mirror.shaderStrength, 0f)
        assertEquals(0.2f, mirror.centerX, 0f)
    }

    @Test fun sameShaderAndNameAsTheFilter() {
        val swirl = SwirlLayer()
        val configured = ConfiguredFilter(swirl, half)
        assertEquals("compiled shader is shared", swirl.shaderId(), configured.shaderId())
        assertEquals(swirl.name, configured.name)
        assertSame(swirl, configured.base)
        assertSame(swirl, swirl.base)
        assertEquals(half, configured.settings)
        assertEquals(FilterSettings.DEFAULT, swirl.settings)
    }

    @Test fun onlyFiltersWithStrengthOrCentreAreAdjustable() {
        assertTrue(ElsaLayer().isAdjustable)
        assertTrue(SwirlLayer().isAdjustable)
        assertTrue(MirrorHorizontalLayer().isAdjustable)
        // Tiny planet has a strength since it was reworked
        assertTrue(TinyPlanetLayer().isAdjustable)
    }

    @Test fun mirrorsKeepTheChosenPart() {
        val mirror = org.pixeldroid.media_editor.photoEdit.imagine.layers.MirrorHorizontalLayer()
        assertEquals("undecided: left part", 1f, ConfiguredFilter(mirror, FilterSettings()).shaderStrength, 0f)
        val end = FilterSettings(mirrorPart = org.pixeldroid.media_editor.photoEdit.imagine.core.types.MirrorPart.END)
        assertEquals("right part", -1f, ConfiguredFilter(mirror, end).shaderStrength, 0f)
        // Only mirrors: a swirl's strength is its own
        assertEquals(1f, ConfiguredFilter(SwirlLayer(), end).shaderStrength, 0f)
    }
}

package org.pixeldroid.media_editor.photoEdit.overlay

import kotlin.math.abs

/**
 * Colour and size for new lines and texts. The colour is chosen with two sliders: hue (around the
 * colour wheel) and lightness (0 = black, 0.5 = the pure colour, 1 = white). Pure, no Android
 * dependencies.
 */
data class ToolStyle(
    /** 0..360 */
    val hue: Float = 0f,
    /** 0..1 */
    val lightness: Float = 0.5f,
    /** Line width relative to the image width */
    val strokeWidth: Float = OverlayDefaults.STROKE_WIDTH,
    /** Text height relative to the image width */
    val textSize: Float = OverlayDefaults.TEXT_SIZE,
) {
    /** ARGB colour, opaque */
    val color: Int get() = hslColor(hue, 1f, lightness)

    companion object {
        val HUE_RANGE = 0f..360f
        val LIGHTNESS_RANGE = 0f..1f
        val STROKE_WIDTH_RANGE = 0.002f..0.03f
        val TEXT_SIZE_RANGE = 0.03f..0.25f
    }
}

/** HSL to an opaque ARGB colour: [hue] in degrees, [saturation] and [lightness] 0..1 */
fun hslColor(hue: Float, saturation: Float, lightness: Float): Int {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val l = lightness.coerceIn(0f, 1f)
    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r, g, b) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    fun channel(v: Float) = Math.round((v + m) * 255f).coerceIn(0, 255)
    return (0xFF shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
}

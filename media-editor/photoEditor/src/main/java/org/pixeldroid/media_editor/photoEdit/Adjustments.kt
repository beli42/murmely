package org.pixeldroid.media_editor.photoEdit

/**
 * The adjustment sliders, in the order they are shown, with their value range. The neutral value
 * of every adjustment is 0. Adding a slider: an entry here, a label (AdjustmentPanel) and a layer
 * (PhotoEditActivity). Pure, no Android dependencies.
 */
enum class Adjustment(val min: Float, val max: Float) {
    EXPOSURE(-1f, 1f),
    // The brightness layer works with very small values
    BRIGHTNESS(-.004f, .004f),
    CONTRAST(-.9f, .9f),
    HIGHLIGHTS(-1f, 1f),
    SHADOWS(-1f, 1f),
    CLARITY(-1f, 1f),
    SATURATION(-1f, 1f),
    VIBRANCE(-1f, 1f),
    WARMTH(-1f, 1f),
    TINT(-1f, 1f),
    SHARPNESS(-1f, 1f),
    VIGNETTE(-1f, 1f),
    // Grain only makes sense in one direction
    GRAIN(0f, 1f);

    fun clamp(value: Float) = value.coerceIn(min, max)
}

/** Values of all adjustments; adjustments at their neutral value 0 are not stored */
data class Sliders(private val values: Map<Adjustment, Float> = emptyMap()) {

    operator fun get(adjustment: Adjustment): Float = values[adjustment] ?: 0f

    /** These sliders with [adjustment] set to [value] (clamped to its range) */
    fun with(adjustment: Adjustment, value: Float): Sliders {
        val clamped = adjustment.clamp(value)
        return Sliders(if (clamped == 0f) values - adjustment else values + (adjustment to clamped))
    }

    /** True if every adjustment is at its neutral value */
    val isNeutral get() = values.isEmpty()
}

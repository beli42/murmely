package org.pixeldroid.media_editor.photoEdit

import kotlin.math.abs
import kotlin.math.roundToInt

/** Display of slider values as -100..+100, independent of the slider's internal range */
object SliderValueFormat {

    /** [value] as a percentage of the larger end of the range [min]..[max] */
    fun percent(value: Float, min: Float, max: Float): Int {
        val maxAbs = maxOf(abs(min), abs(max))
        return if (maxAbs == 0f) 0 else (value / maxAbs * 100).roundToInt()
    }

    /** "+35", "−20" (real minus sign) or "0" */
    fun label(percent: Int): String = when {
        percent > 0 -> "+$percent"
        percent < 0 -> "\u2212${-percent}"
        else -> "0"
    }
}

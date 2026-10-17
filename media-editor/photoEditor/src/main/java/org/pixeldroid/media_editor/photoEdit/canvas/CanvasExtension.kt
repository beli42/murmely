package org.pixeldroid.media_editor.photoEdit.canvas

import org.pixeldroid.media_editor.photoEdit.overlay.hslColor
import kotlin.math.min
import kotlin.math.roundToInt

/** A side of the photo */
enum class Side { TOP, BOTTOM, LEFT, RIGHT }

/**
 * Area relative to the photo: the photo is 0..1 in both directions (y down), the canvas around
 * it reaches beyond. Texts, stickers and drawings may lie anywhere inside.
 */
data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    companion object {
        /** Just the photo */
        val PHOTO = Bounds(0f, 0f, 1f, 1f)
    }
}

/**
 * The canvas made larger than the photo: [size] (relative to the photo's shorter side) added on
 * each of [sides], in the colour [hue] / [lightness] (0 black .. 1 white). Only the photo gets
 * filters and sliders, not the added area.
 */
data class CanvasExtension(
    val size: Float = 0f,
    val sides: Set<Side> = Side.entries.toSet(),
    val hue: Float = 0f,
    val lightness: Float = 1f,
) {
    /** ARGB colour, opaque */
    val color: Int get() = hslColor(hue, 1f, lightness)

    /** Nothing added */
    val isNone: Boolean get() = sides.isEmpty() || size <= 0f

    /** Pixels added on [side] of a photo of [width] x [height] */
    fun pixels(side: Side, width: Int, height: Int): Int =
        if (side in sides) (size.coerceIn(SIZE_RANGE) * min(width, height)).roundToInt() else 0

    /** Size of the canvas for a photo of [width] x [height] */
    fun canvasSize(width: Int, height: Int): Pair<Int, Int> =
        (width + pixels(Side.LEFT, width, height) + pixels(Side.RIGHT, width, height)) to
            (height + pixels(Side.TOP, width, height) + pixels(Side.BOTTOM, width, height))

    /** Where the photo's top left corner is on the canvas, in pixels */
    fun photoOffset(width: Int, height: Int): Pair<Int, Int> =
        pixels(Side.LEFT, width, height) to pixels(Side.TOP, width, height)

    /** The canvas relative to the photo (see [Bounds]) */
    fun bounds(width: Int, height: Int): Bounds = Bounds(
        // 0 - x instead of -x: no negative zero, so no extension equals Bounds.PHOTO
        0f - pixels(Side.LEFT, width, height).toFloat() / width,
        0f - pixels(Side.TOP, width, height).toFloat() / height,
        1f + pixels(Side.RIGHT, width, height).toFloat() / width,
        1f + pixels(Side.BOTTOM, width, height).toFloat() / height,
    )

    companion object {
        /** Up to the photo's shorter side on each chosen side */
        val SIZE_RANGE = 0f..1f
    }
}

package org.pixeldroid.media_editor.photoEdit.overlay

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Moving, scaling and turning stickers, and finding them under a finger. Pure, no Android.
 *
 * Positions are relative to the image (0..1 in both directions, y down). Because the image is
 * usually not square, the calculations happen in a space where both directions use the image
 * width as unit (y is multiplied by [imageAspect] = image height / image width): there a turn
 * keeps the sticker's shape.
 */
object StickerGeometry {

    /** Smallest and largest sticker, relative to the image width */
    const val MIN_WIDTH = 0.05f
    const val MAX_WIDTH = 1f

    private data class Vec(val x: Float, val y: Float) {
        operator fun minus(o: Vec) = Vec(x - o.x, y - o.y)
        operator fun plus(o: Vec) = Vec(x + o.x, y + o.y)
        val length get() = hypot(x, y)
        /** Degrees, clockwise from the x axis (y down) */
        val angle get() = Math.toDegrees(atan2(y, x).toDouble()).toFloat()
        fun rotated(degrees: Float): Vec {
            val r = Math.toRadians(degrees.toDouble())
            val c = cos(r).toFloat()
            val s = sin(r).toFloat()
            return Vec(x * c - y * s, x * s + y * c)
        }
    }

    private fun RelativePoint.toSpace(imageAspect: Float) = Vec(x, y * imageAspect)
    private fun Vec.toRelative(imageAspect: Float) = RelativePoint(x, y / imageAspect)
    private fun StickerItem.centre(imageAspect: Float) = Vec(x, y * imageAspect)

    /** Half width and half height of [sticker], in image widths */
    private fun halfSize(sticker: StickerItem) = Vec(sticker.width / 2, sticker.width * sticker.aspect / 2)

    /** [point] is on [sticker] (turned), with [margin] (image widths) around it */
    fun contains(sticker: StickerItem, point: RelativePoint, imageAspect: Float, margin: Float = 0f): Boolean {
        val local = (point.toSpace(imageAspect) - sticker.centre(imageAspect)).rotated(-sticker.rotation)
        val half = halfSize(sticker)
        return abs(local.x) <= half.x + margin && abs(local.y) <= half.y + margin
    }

    /** The four corners of [sticker] (turned): top left, top right, bottom right, bottom left */
    fun corners(sticker: StickerItem, imageAspect: Float): List<RelativePoint> {
        val half = halfSize(sticker)
        val centre = sticker.centre(imageAspect)
        return listOf(Vec(-half.x, -half.y), Vec(half.x, -half.y), Vec(half.x, half.y), Vec(-half.x, half.y))
            .map { (it.rotated(sticker.rotation) + centre).toRelative(imageAspect) }
    }

    /** The handle for scaling and turning: the bottom right corner */
    fun handle(sticker: StickerItem, imageAspect: Float): RelativePoint = corners(sticker, imageAspect)[2]

    /** [sticker] moved by the finger going from [from] to [to] */
    fun move(sticker: StickerItem, from: RelativePoint, to: RelativePoint): StickerItem =
        sticker.copy(x = (sticker.x + to.x - from.x).coerceIn(0f, 1f), y = (sticker.y + to.y - from.y).coerceIn(0f, 1f))

    /**
     * [sticker] scaled and turned around its centre by the handle going from [from] to [to]:
     * the distance to the centre gives the size, the direction the angle.
     */
    fun scaleAndTurn(sticker: StickerItem, from: RelativePoint, to: RelativePoint, imageAspect: Float): StickerItem {
        val centre = sticker.centre(imageAspect)
        val before = from.toSpace(imageAspect) - centre
        val after = to.toSpace(imageAspect) - centre
        if (before.length < 1e-6f || after.length < 1e-6f) return sticker
        return sticker.copy(
            width = (sticker.width * after.length / before.length).coerceIn(MIN_WIDTH, MAX_WIDTH),
            rotation = normalise(sticker.rotation + after.angle - before.angle),
        )
    }

    /**
     * [sticker] after two fingers went from ([from1], [from2]) to ([to1], [to2]): moved with
     * their middle, scaled with their distance, turned with the line between them.
     */
    fun pinch(
        sticker: StickerItem,
        from1: RelativePoint, from2: RelativePoint,
        to1: RelativePoint, to2: RelativePoint,
        imageAspect: Float,
    ): StickerItem {
        val a0 = from1.toSpace(imageAspect)
        val b0 = from2.toSpace(imageAspect)
        val a1 = to1.toSpace(imageAspect)
        val b1 = to2.toSpace(imageAspect)
        val before = b0 - a0
        val after = b1 - a1
        if (before.length < 1e-6f || after.length < 1e-6f) return sticker
        val shift = Vec((a1.x + b1.x - a0.x - b0.x) / 2, (a1.y + b1.y - a0.y - b0.y) / 2)
        val centre = (sticker.centre(imageAspect) + shift).toRelative(imageAspect)
        return sticker.copy(
            x = centre.x.coerceIn(0f, 1f),
            y = centre.y.coerceIn(0f, 1f),
            width = (sticker.width * after.length / before.length).coerceIn(MIN_WIDTH, MAX_WIDTH),
            rotation = normalise(sticker.rotation + after.angle - before.angle),
        )
    }

    /** [degrees] in -180..180 */
    fun normalise(degrees: Float): Float {
        var d = degrees % 360f
        if (d > 180f) d -= 360f
        if (d <= -180f) d += 360f
        return d
    }
}

/**
 * The bin shown while a sticker is dragged: a circle at the bottom centre of the shown image,
 * in pixels. Dropping a sticker there removes it. Pure, no Android.
 */
object StickerBin {

    /** Centre of the bin in a view of [width] x [height]; [radius] and [margin] in pixels */
    fun centre(width: Int, height: Int, radius: Float, margin: Float): Pair<Float, Float> =
        width / 2f to (height - margin - radius).coerceAtLeast(radius)

    /** The finger at ([x], [y]) is on the bin (a little larger than drawn, so it is easy to hit) */
    fun contains(x: Float, y: Float, width: Int, height: Int, radius: Float, margin: Float): Boolean {
        val (cx, cy) = centre(width, height, radius, margin)
        return hypot(x - cx, y - cy) <= radius * 1.4f
    }
}

package org.pixeldroid.media_editor.photoEdit.overlay

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A text or sticker as a turned rectangle: centre ([x], [y], relative to the image), [width] and
 * [height] in image widths, [rotation] in degrees clockwise.
 */
data class ItemBox(val x: Float, val y: Float, val width: Float, val height: Float, val rotation: Float = 0f)

/**
 * Moving, scaling and turning texts and stickers, and finding them under a finger. Pure, no Android.
 *
 * Positions are relative to the image (0..1 in both directions, y down). Because the image is
 * usually not square, the calculations happen in a space where both directions use the image
 * width as unit (y is multiplied by imageAspect = image height / image width): there a turn
 * keeps the item's shape.
 */
object ItemGeometry {

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
    private fun ItemBox.centre(imageAspect: Float) = Vec(x, y * imageAspect)

    /** [point] is on [box] (turned), with [margin] (image widths) around it */
    fun contains(box: ItemBox, point: RelativePoint, imageAspect: Float, margin: Float = 0f): Boolean {
        val local = (point.toSpace(imageAspect) - box.centre(imageAspect)).rotated(-box.rotation)
        return abs(local.x) <= box.width / 2 + margin && abs(local.y) <= box.height / 2 + margin
    }

    /** The four corners of [box] (turned): top left, top right, bottom right, bottom left */
    fun corners(box: ItemBox, imageAspect: Float): List<RelativePoint> {
        val hx = box.width / 2
        val hy = box.height / 2
        val centre = box.centre(imageAspect)
        return listOf(Vec(-hx, -hy), Vec(hx, -hy), Vec(hx, hy), Vec(-hx, hy))
            .map { (it.rotated(box.rotation) + centre).toRelative(imageAspect) }
    }

    /** The handle for scaling and turning: the bottom right corner */
    fun handle(box: ItemBox, imageAspect: Float): RelativePoint = corners(box, imageAspect)[2]

    /** [box] moved by the finger going from [from] to [to], its centre staying on the image */
    fun move(box: ItemBox, from: RelativePoint, to: RelativePoint): ItemBox =
        box.copy(x = (box.x + to.x - from.x).coerceIn(0f, 1f), y = (box.y + to.y - from.y).coerceIn(0f, 1f))

    /**
     * [box] scaled and turned around its centre by the handle going from [from] to [to]: the
     * distance to the centre gives the size, the direction the angle. Not limited (see the callers).
     */
    fun scaleAndTurn(box: ItemBox, from: RelativePoint, to: RelativePoint, imageAspect: Float): ItemBox {
        val centre = box.centre(imageAspect)
        val before = from.toSpace(imageAspect) - centre
        val after = to.toSpace(imageAspect) - centre
        if (before.length < 1e-6f || after.length < 1e-6f) return box
        val factor = after.length / before.length
        return box.copy(
            width = box.width * factor,
            height = box.height * factor,
            rotation = normalise(box.rotation + after.angle - before.angle),
        )
    }

    /**
     * [box] after two fingers went from ([from1], [from2]) to ([to1], [to2]): moved with
     * their middle, scaled with their distance, turned with the line between them.
     */
    fun pinch(
        box: ItemBox,
        from1: RelativePoint, from2: RelativePoint,
        to1: RelativePoint, to2: RelativePoint,
        imageAspect: Float,
    ): ItemBox {
        val a0 = from1.toSpace(imageAspect)
        val b0 = from2.toSpace(imageAspect)
        val a1 = to1.toSpace(imageAspect)
        val b1 = to2.toSpace(imageAspect)
        val before = b0 - a0
        val after = b1 - a1
        if (before.length < 1e-6f || after.length < 1e-6f) return box
        val shift = Vec((a1.x + b1.x - a0.x - b0.x) / 2, (a1.y + b1.y - a0.y - b0.y) / 2)
        val centre = (box.centre(imageAspect) + shift).toRelative(imageAspect)
        val factor = after.length / before.length
        return box.copy(
            x = centre.x.coerceIn(0f, 1f),
            y = centre.y.coerceIn(0f, 1f),
            width = box.width * factor,
            height = box.height * factor,
            rotation = normalise(box.rotation + after.angle - before.angle),
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

/** [ItemGeometry] for stickers: their box, and the sticker for a changed box */
object StickerGeometry {

    /** Smallest and largest sticker, relative to the image width */
    const val MIN_WIDTH = 0.05f
    const val MAX_WIDTH = 1f

    fun box(sticker: StickerItem) = ItemBox(sticker.x, sticker.y, sticker.width, sticker.width * sticker.aspect, sticker.rotation)

    /** [sticker] placed like [box] (size limited) */
    fun withBox(sticker: StickerItem, box: ItemBox) = sticker.copy(
        x = box.x, y = box.y, width = box.width.coerceIn(MIN_WIDTH, MAX_WIDTH), rotation = box.rotation,
    )

    fun contains(sticker: StickerItem, point: RelativePoint, imageAspect: Float, margin: Float = 0f) =
        ItemGeometry.contains(box(sticker), point, imageAspect, margin)

    fun corners(sticker: StickerItem, imageAspect: Float) = ItemGeometry.corners(box(sticker), imageAspect)

    fun handle(sticker: StickerItem, imageAspect: Float) = ItemGeometry.handle(box(sticker), imageAspect)

    fun move(sticker: StickerItem, from: RelativePoint, to: RelativePoint) =
        withBox(sticker, ItemGeometry.move(box(sticker), from, to))

    fun scaleAndTurn(sticker: StickerItem, from: RelativePoint, to: RelativePoint, imageAspect: Float) =
        withBox(sticker, ItemGeometry.scaleAndTurn(box(sticker), from, to, imageAspect))

    fun pinch(
        sticker: StickerItem,
        from1: RelativePoint, from2: RelativePoint,
        to1: RelativePoint, to2: RelativePoint,
        imageAspect: Float,
    ) = withBox(sticker, ItemGeometry.pinch(box(sticker), from1, from2, to1, to2, imageAspect))

    fun normalise(degrees: Float) = ItemGeometry.normalise(degrees)
}

/**
 * [ItemGeometry] for texts. A text is centred at (x, y); its width is measured with the real font
 * by the caller (relative to the image width), its height is [HEIGHT] times its size.
 */
object TextGeometry {

    /** Height of a line of text relative to the text size (ascent + descent of the font) */
    const val HEIGHT = 1.2f

    /** Smallest and largest text size, relative to the image width */
    const val MIN_SIZE = 0.02f
    const val MAX_SIZE = 0.5f

    fun box(text: TextItem, width: Float) = ItemBox(text.x, text.y, width, text.size * HEIGHT, text.rotation)

    /** [text] placed like [box], scaled by how much [box] grew compared to [start] (size limited) */
    fun withBox(text: TextItem, start: ItemBox, box: ItemBox): TextItem {
        val factor = if (start.width > 0f) box.width / start.width else 1f
        return text.copy(
            x = box.x, y = box.y,
            size = (text.size * factor).coerceIn(MIN_SIZE, MAX_SIZE),
            rotation = box.rotation,
        )
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

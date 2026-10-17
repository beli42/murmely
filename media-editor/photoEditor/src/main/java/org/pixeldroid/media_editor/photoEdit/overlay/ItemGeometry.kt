package org.pixeldroid.media_editor.photoEdit.overlay

import org.pixeldroid.media_editor.photoEdit.canvas.Bounds

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

    /** [box] moved by the finger going from [from] to [to], its centre staying inside [bounds] */
    fun move(box: ItemBox, from: RelativePoint, to: RelativePoint, bounds: Bounds = Bounds.PHOTO): ItemBox = box.copy(
        x = (box.x + to.x - from.x).coerceIn(bounds.left, bounds.right),
        y = (box.y + to.y - from.y).coerceIn(bounds.top, bounds.bottom),
    )

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

    /**
     * [box] moved (and if it is larger than [bounds], made smaller) so that all of it is inside
     * [bounds] (the photo, or the canvas around it), turned as it is: the rectangle around the
     * turned box stays inside.
     */
    fun keepInside(box: ItemBox, imageAspect: Float, bounds: Bounds = Bounds.PHOTO): ItemBox {
        val r = Math.toRadians(box.rotation.toDouble())
        val c = abs(cos(r).toFloat())
        val s = abs(sin(r).toFloat())
        // Half size of the rectangle around the turned box, in image widths
        val ex = box.width / 2 * c + box.height / 2 * s
        val ey = box.width / 2 * s + box.height / 2 * c
        if (ex <= 0f || ey <= 0f) return box
        // The bounds in these units: x as is, y times imageAspect
        val left = bounds.left
        val right = bounds.right
        val top = bounds.top * imageAspect
        val bottom = bounds.bottom * imageAspect
        val factor = minOf(1f, (right - left) / 2 / ex, (bottom - top) / 2 / ey)
        val hx = ex * factor
        val hy = ey * factor
        val x = box.x.coerceIn(left + hx, right - hx)
        val y = (box.y * imageAspect).coerceIn(top + hy, bottom - hy) / imageAspect
        return box.copy(x = x, y = y, width = box.width * factor, height = box.height * factor)
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

    /**
     * The box of [text] of [width] (its letters, measured with letter spacing), grown by its
     * outline and shadows so that keeping it on the image keeps them on the image too
     */
    fun box(text: TextItem, width: Float): ItemBox {
        val (extraX, extraY) = effectsExtent(text)
        return ItemBox(text.x, text.y, width + 2 * extraX, text.size * HEIGHT + 2 * extraY, text.rotation)
    }

    /**
     * How far outline and shadows reach beyond the letters on each side (x, y), relative to the
     * image width. The drop shadow counts on both sides, so the text stays centred.
     */
    fun effectsExtent(text: TextItem): Pair<Float, Float> {
        val e = text.effects
        val outline = e.outline * text.size
        val soft = e.shadow * (TextEffects.SHADOW_BLUR + TextEffects.SHADOW_OFFSET) * text.size
        val angle = Math.toRadians(e.dropShadowAngle.toDouble())
        val dropX = kotlin.math.abs(cos(angle).toFloat()) * e.dropShadow * text.size
        val dropY = kotlin.math.abs(sin(angle).toFloat()) * e.dropShadow * text.size
        return (outline + kotlin.math.max(soft, dropX)) to (outline + kotlin.math.max(soft, dropY))
    }

    /**
     * [text] (of [width], relative to the image width) moved and if needed made smaller, so all of
     * it is on the image
     */
    fun keepInside(text: TextItem, width: Float, imageAspect: Float, bounds: Bounds = Bounds.PHOTO): TextItem {
        val box = box(text, width)
        return withBox(text, box, ItemGeometry.keepInside(box, imageAspect, bounds))
    }

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

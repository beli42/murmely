package org.pixeldroid.media_editor.photoEdit.crop

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Part of an image, relative to it (0..1, y down). Pure, no Android dependencies.
 */
data class CropRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2
    val centerY: Float get() = (top + bottom) / 2

    /** The same part after turning the image 90° clockwise [quarterTurns] times */
    fun rotated(quarterTurns: Int): CropRect = when (Math.floorMod(quarterTurns, 4)) {
        // A point (x, y) goes to (1 - y, x)
        1 -> CropRect(1 - bottom, left, 1 - top, right)
        2 -> CropRect(1 - right, 1 - bottom, 1 - left, 1 - top)
        3 -> CropRect(top, 1 - right, bottom, 1 - left)
        else -> this
    }

    companion object {
        val FULL = CropRect(0f, 0f, 1f, 1f)
    }
}

/** Fixed width : height of the crop, or free */
enum class CropAspect(val ratio: Float?) {
    FREE(null),
    /** Same shape as the (turned) image */
    ORIGINAL(null),
    SQUARE(1f),
    LANDSCAPE_4_3(4f / 3),
    PORTRAIT_3_4(3f / 4),
    LANDSCAPE_16_9(16f / 9),
    PORTRAIT_9_16(9f / 16);

    /** Width : height in pixels for an image of that shape, null if free */
    fun pixelRatio(imageAspect: Float): Float? = if (this == ORIGINAL) imageAspect else ratio
}

/**
 * The crop of the original image: first turned [quarterTurns] times 90° clockwise, then
 * straightened by [angle] degrees (clockwise, -45..45) and enlarged just enough to fill its
 * frame again (no empty corners, see [CropGeometry.straightenScale]), then [rect] of that is kept.
 */
data class CropSettings(
    val rect: CropRect = CropRect.FULL,
    val quarterTurns: Int = 0,
    val aspect: CropAspect = CropAspect.FREE,
    val angle: Float = 0f,
) {
    /** Nothing cut off, not turned: the original image */
    val isOriginal: Boolean get() = rect == CropRect.FULL && Math.floorMod(quarterTurns, 4) == 0 && angle == 0f
}

/** Which part of the crop frame is dragged */
enum class CropHandle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, LEFT, TOP, RIGHT, BOTTOM, MOVE }

object CropGeometry {

    /** Smallest crop, relative to the image */
    const val MIN_SIZE = 0.05f

    /**
     * Width : height of an image of [width] x [height] after [quarterTurns] turns
     */
    fun turnedAspect(width: Int, height: Int, quarterTurns: Int): Float =
        if (Math.floorMod(quarterTurns, 2) == 1) height.toFloat() / width else width.toFloat() / height

    /**
     * The largest crop with width : height [pixelRatio] (in pixels of an image with width : height
     * [imageAspect]) around the centre of [rect], inside the image. Free ([pixelRatio] null): [rect].
     */
    fun withAspect(rect: CropRect, pixelRatio: Float?, imageAspect: Float): CropRect {
        if (pixelRatio == null) return rect
        // Relative width : height that gives the pixel ratio
        val relative = pixelRatio / imageAspect
        // As large as the image allows, then centred on the current crop as far as possible
        var w = 1f
        var h = w / relative
        if (h > 1f) { h = 1f; w = h * relative }
        val left = (rect.centerX - w / 2).coerceIn(0f, 1f - w)
        val top = (rect.centerY - h / 2).coerceIn(0f, 1f - h)
        return CropRect(left, top, left + w, top + h)
    }

    /**
     * [rect] after dragging [handle] by ([dx], [dy]) (relative to the image). Stays inside the
     * image and at least [MIN_SIZE]. With [pixelRatio] the shape is kept: corners resize from the
     * opposite corner, edges are ignored.
     */
    fun drag(rect: CropRect, handle: CropHandle, dx: Float, dy: Float, pixelRatio: Float?, imageAspect: Float): CropRect {
        if (handle == CropHandle.MOVE) {
            val x = dx.coerceIn(-rect.left, 1f - rect.right)
            val y = dy.coerceIn(-rect.top, 1f - rect.bottom)
            return CropRect(rect.left + x, rect.top + y, rect.right + x, rect.bottom + y)
        }
        if (pixelRatio != null) return dragKeepingShape(rect, handle, dx, dy, pixelRatio / imageAspect)

        var (left, top, right, bottom) = rect
        if (handle in setOf(CropHandle.LEFT, CropHandle.TOP_LEFT, CropHandle.BOTTOM_LEFT))
            left = (left + dx).coerceIn(0f, right - MIN_SIZE)
        if (handle in setOf(CropHandle.RIGHT, CropHandle.TOP_RIGHT, CropHandle.BOTTOM_RIGHT))
            right = (right + dx).coerceIn(left + MIN_SIZE, 1f)
        if (handle in setOf(CropHandle.TOP, CropHandle.TOP_LEFT, CropHandle.TOP_RIGHT))
            top = (top + dy).coerceIn(0f, bottom - MIN_SIZE)
        if (handle in setOf(CropHandle.BOTTOM, CropHandle.BOTTOM_LEFT, CropHandle.BOTTOM_RIGHT))
            bottom = (bottom + dy).coerceIn(top + MIN_SIZE, 1f)
        return CropRect(left, top, right, bottom)
    }

    /** Corner drag with relative width : height [relative], from the opposite corner */
    private fun dragKeepingShape(rect: CropRect, handle: CropHandle, dx: Float, dy: Float, relative: Float): CropRect {
        val growsLeft = handle == CropHandle.TOP_LEFT || handle == CropHandle.BOTTOM_LEFT
        val growsUp = handle == CropHandle.TOP_LEFT || handle == CropHandle.TOP_RIGHT
        if (handle !in setOf(CropHandle.TOP_LEFT, CropHandle.TOP_RIGHT, CropHandle.BOTTOM_LEFT, CropHandle.BOTTOM_RIGHT)) return rect
        // Fixed corner
        val fixedX = if (growsLeft) rect.right else rect.left
        val fixedY = if (growsUp) rect.bottom else rect.top
        // Size the finger asks for: the direction it moved more decides
        val wantedW = rect.width + if (growsLeft) -dx else dx
        val wantedH = rect.height + if (growsUp) -dy else dy
        val wanted = if (abs(dx) >= abs(dy * relative)) wantedW else wantedH * relative
        // Room up to the image border in both directions
        val roomW = if (growsLeft) fixedX else 1f - fixedX
        val roomH = if (growsUp) fixedY else 1f - fixedY
        val maxW = min(roomW, roomH * relative)
        val minW = min(max(MIN_SIZE, MIN_SIZE * relative), maxW)
        val w = wanted.coerceIn(minW, maxW)
        val h = w / relative
        val left = if (growsLeft) fixedX - w else fixedX
        val top = if (growsUp) fixedY - h else fixedY
        return CropRect(left, top, left + w, top + h)
    }

    /**
     * Where the crop is in the original image of [width] x [height] pixels (before turning), in
     * pixels: left, top, width, height. At least 1 x 1.
     */
    fun sourcePixels(settings: CropSettings, width: Int, height: Int): IntArray {
        // Undo the turns: the rect in the unturned image
        val r = settings.rect.rotated(-settings.quarterTurns)
        val left = (r.left * width).toInt().coerceIn(0, width - 1)
        val top = (r.top * height).toInt().coerceIn(0, height - 1)
        val right = Math.round(r.right * width).coerceIn(left + 1, width)
        val bottom = Math.round(r.bottom * height).coerceIn(top + 1, height)
        return intArrayOf(left, top, right - left, bottom - top)
    }

    /** The crop settings after turning by [turns] more quarter turns (the crop turns with the image) */
    fun turn(settings: CropSettings, turns: Int): CropSettings =
        settings.copy(rect = settings.rect.rotated(turns), quarterTurns = Math.floorMod(settings.quarterTurns + turns, 4))

    /**
     * Turns the image of [width] x [height] (unturned) by [turns] quarter turns; a fixed shape
     * stays the same shape on screen (4:3 stays 4:3, the crop is fitted again)
     */
    /** Straightening goes this far either way (degrees) */
    const val MAX_ANGLE = 45f

    /**
     * How much an image of [width] x [height] turned by [degrees] must be enlarged so that it
     * still covers its own, unturned frame: no empty corners. 1 when not turned.
     */
    fun straightenScale(width: Float, height: Float, degrees: Float): Float {
        if (degrees == 0f || width <= 0f || height <= 0f) return 1f
        val a = Math.toRadians(abs(degrees).toDouble())
        val cos = kotlin.math.cos(a).toFloat()
        val sin = kotlin.math.sin(a).toFloat()
        return max((width * cos + height * sin) / width, (width * sin + height * cos) / height)
    }

    /** Straightened by [degrees] (limited to ±[MAX_ANGLE]); the crop stays as it is */
    fun straighten(settings: CropSettings, degrees: Float): CropSettings =
        settings.copy(angle = degrees.coerceIn(-MAX_ANGLE, MAX_ANGLE))

    fun turnBy(settings: CropSettings, turns: Int, width: Int, height: Int): CropSettings {
        val turned = turn(settings, turns)
        val aspect = turnedAspect(width, height, turned.quarterTurns)
        return turned.copy(rect = withAspect(turned.rect, turned.aspect.pixelRatio(aspect), aspect))
    }

    /** Chooses the shape [aspect] for an image of [width] x [height] (unturned) */
    fun setAspect(settings: CropSettings, aspect: CropAspect, width: Int, height: Int): CropSettings {
        val imageAspect = turnedAspect(width, height, settings.quarterTurns)
        return settings.copy(aspect = aspect, rect = withAspect(settings.rect, aspect.pixelRatio(imageAspect), imageAspect))
    }

    /**
     * The handle of [rect] at ([x], [y]) (relative to the image), within [toleranceX] /
     * [toleranceY]; corners before edges, inside the crop moves it. Edges only without a fixed
     * shape ([shapeFixed]). Null: outside, nothing to drag.
     */
    fun handleAt(rect: CropRect, x: Float, y: Float, toleranceX: Float, toleranceY: Float, shapeFixed: Boolean): CropHandle? {
        val nearLeft = abs(x - rect.left) <= toleranceX
        val nearRight = abs(x - rect.right) <= toleranceX
        val nearTop = abs(y - rect.top) <= toleranceY
        val nearBottom = abs(y - rect.bottom) <= toleranceY
        val withinX = x >= rect.left - toleranceX && x <= rect.right + toleranceX
        val withinY = y >= rect.top - toleranceY && y <= rect.bottom + toleranceY
        // A small crop: the corner nearest to the finger
        val left = nearLeft && (!nearRight || abs(x - rect.left) <= abs(x - rect.right))
        val right = nearRight && !left
        val top = nearTop && (!nearBottom || abs(y - rect.top) <= abs(y - rect.bottom))
        val bottom = nearBottom && !top
        return when {
            left && top -> CropHandle.TOP_LEFT
            right && top -> CropHandle.TOP_RIGHT
            left && bottom -> CropHandle.BOTTOM_LEFT
            right && bottom -> CropHandle.BOTTOM_RIGHT
            !shapeFixed && left && withinY -> CropHandle.LEFT
            !shapeFixed && right && withinY -> CropHandle.RIGHT
            !shapeFixed && top && withinX -> CropHandle.TOP
            !shapeFixed && bottom && withinX -> CropHandle.BOTTOM
            x > rect.left && x < rect.right && y > rect.top && y < rect.bottom -> CropHandle.MOVE
            else -> null
        }
    }
}

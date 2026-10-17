package org.pixeldroid.media_editor.photoEdit.overlay

/**
 * Drawings, texts and stickers on top of the image. Everything is relative to the image, not to
 * the screen: positions from 0 (left/top) to 1 (right/bottom), sizes as a fraction of the image
 * width. So the same overlay is drawn identically in the preview (any screen size) and in the
 * saved full resolution image. Pure data, no Android dependencies.
 */
data class Overlay(
    val strokes: List<Stroke> = emptyList(),
    val texts: List<TextItem> = emptyList(),
    val stickers: List<StickerItem> = emptyList(),
) {
    val isEmpty get() = strokes.isEmpty() && texts.isEmpty() && stickers.isEmpty()

    operator fun plus(stroke: Stroke) = copy(strokes = strokes + stroke)
    operator fun plus(text: TextItem) = copy(texts = texts + text)
    operator fun plus(sticker: StickerItem) = copy(stickers = stickers + sticker)

    /** Id for a new text or sticker */
    fun nextId(): Long = ((texts.map { it.id } + stickers.map { it.id }).maxOrNull() ?: 0L) + 1

    /** Layer for a new text or sticker: in front of all others */
    fun nextLayer(): Long = ((texts.map { it.layer } + stickers.map { it.layer }).maxOrNull() ?: 0L) + 1

    /**
     * Ids of all texts and stickers from back to front. Items with the same layer keep the old
     * order: texts behind stickers, each in the order they were added.
     */
    val layerOrder: List<Long>
        get() = (texts.map { it.layer to it.id } + stickers.map { it.layer to it.id }).sortedBy { it.first }.map { it.second }

    /** The item [id] can move [steps] layers (+ to the front, - to the back) */
    fun canMoveLayer(id: Long, steps: Int): Boolean {
        val index = layerOrder.indexOf(id)
        return index >= 0 && steps != 0 && index + steps in layerOrder.indices
    }

    /** The item [id] moved [steps] layers to the front (negative: to the back), past other texts and stickers */
    fun moveLayer(id: Long, steps: Int): Overlay {
        if (!canMoveLayer(id, steps)) return this
        val order = layerOrder.toMutableList()
        val index = order.indexOf(id)
        order.removeAt(index)
        order.add(index + steps, id)
        // Layers 1, 2, 3 ... in the new order
        val layers = order.withIndex().associate { (i, itemId) -> itemId to (i + 1L) }
        return copy(
            texts = texts.map { it.copy(layer = layers.getValue(it.id)) },
            stickers = stickers.map { it.copy(layer = layers.getValue(it.id)) },
        )
    }

    fun moveText(id: Long, x: Float, y: Float) =
        copy(texts = texts.map { if (it.id == id) it.copy(x = x, y = y) else it })

    /** Changes a text; blank text removes it */
    fun editText(id: Long, text: String) =
        if (text.isBlank()) copy(texts = texts.filterNot { it.id == id })
        else copy(texts = texts.map { if (it.id == id) it.copy(text = text) else it })

    fun moveSticker(id: Long, x: Float, y: Float) =
        copy(stickers = stickers.map { if (it.id == id) it.copy(x = x, y = y) else it })

    fun removeSticker(id: Long) = copy(stickers = stickers.filterNot { it.id == id })

    fun removeText(id: Long) = copy(texts = texts.filterNot { it.id == id })

    /** Puts [text] in place of the text with its id (same place in the drawing order) */
    fun replaceText(text: TextItem) = copy(texts = texts.map { if (it.id == text.id) text else it })

    /** Moves, scales and turns a text */
    fun transformText(id: Long, x: Float, y: Float, size: Float, rotation: Float) =
        copy(texts = texts.map { if (it.id == id) it.copy(x = x, y = y, size = size, rotation = rotation) else it })

    /** Moves, scales and turns a sticker */
    fun transformSticker(id: Long, x: Float, y: Float, width: Float, rotation: Float) =
        copy(stickers = stickers.map { if (it.id == id) it.copy(x = x, y = y, width = width, rotation = rotation) else it })
}

/** A point on the image, 0..1 in both directions */
data class RelativePoint(val x: Float, val y: Float)

/** A drawn line; [width] is relative to the image width */
data class Stroke(
    val points: List<RelativePoint>,
    val width: Float = OverlayDefaults.STROKE_WIDTH,
    val color: Int = OverlayDefaults.COLOR,
)

/**
 * One line of text centred at (x, y); [size] is the text height relative to the image width.
 * Turned by [rotation] degrees clockwise around its centre.
 */
data class TextItem(
    val text: String,
    val x: Float,
    val y: Float,
    val size: Float = OverlayDefaults.TEXT_SIZE,
    val color: Int = OverlayDefaults.COLOR,
    /** Identifies the text when it is moved or edited */
    val id: Long = 0,
    /** Degrees clockwise, -180..180 */
    val rotation: Float = 0f,
    val font: TextFont = TextFont.DEFAULT,
    /** Stacking order among texts and stickers: higher is in front (see [Overlay.layerOrder]) */
    val layer: Long = 0,
    val effects: TextEffects = TextEffects(),
)

/**
 * Outline, letter spacing and shadows of a text; all relative to the text size, so they scale
 * with it. Each is off at 0.
 */
data class TextEffects(
    /** Width of the outline around the letters */
    val outline: Float = 0f,
    val outlineColor: Int = OverlayDefaults.BLACK,
    /** Extra space between letters, in text sizes (negative: closer) */
    val letterSpacing: Float = 0f,
    /** Soft shadow: 0 none .. 1 strong (blur and distance together) */
    val shadow: Float = 0f,
    /** Hard drop shadow: its distance from the text */
    val dropShadow: Float = 0f,
    val dropShadowColor: Int = OverlayDefaults.BLACK,
    /** Direction of the drop shadow, degrees clockwise from right (45: right and down) */
    val dropShadowAngle: Float = 45f,
) {
    companion object {
        val OUTLINE_RANGE = 0f..0.25f
        val LETTER_SPACING_RANGE = -0.1f..0.5f
        val SHADOW_RANGE = 0f..1f
        val DROP_SHADOW_RANGE = 0f..0.3f
        val ANGLE_RANGE = 0f..360f

        /** Colours to pick for outline and drop shadow: black, white, grey, red, yellow, blue */
        val COLORS = listOf(
            0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF808080.toInt(),
            0xFFE53935.toInt(), 0xFFFDD835.toInt(), 0xFF1E88E5.toInt(),
        )

        /** Blur radius and distance of the soft shadow, in text sizes, at strength 1 */
        const val SHADOW_BLUR = 0.15f
        const val SHADOW_OFFSET = 0.05f
    }
}

/**
 * A sticker image centred at (x, y); [width] is relative to the image width, the height follows
 * its aspect ratio. Turned by [rotation] degrees clockwise around its centre.
 */
data class StickerItem(
    val uri: String,
    val x: Float,
    val y: Float,
    val width: Float = OverlayDefaults.STICKER_WIDTH,
    /** Height / width of the sticker image */
    val aspect: Float = 1f,
    /** Identifies the sticker when it is moved */
    val id: Long = 0,
    /** Degrees clockwise, -180..180 */
    val rotation: Float = 0f,
    /** Stacking order among texts and stickers: higher is in front (see [Overlay.layerOrder]) */
    val layer: Long = 0,
)

object OverlayDefaults {
    /** About as thick as the 5 px line on a typical phone screen before */
    const val STROKE_WIDTH = 0.006f
    const val TEXT_SIZE = 0.1f
    const val STICKER_WIDTH = 0.2f
    const val COLOR: Int = 0xFFFF0000.toInt()   // red
    const val BLACK: Int = 0xFF000000.toInt()
}

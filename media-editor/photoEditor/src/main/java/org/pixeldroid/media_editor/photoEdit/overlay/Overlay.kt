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

    fun moveText(id: Long, x: Float, y: Float) =
        copy(texts = texts.map { if (it.id == id) it.copy(x = x, y = y) else it })

    /** Changes a text; blank text removes it */
    fun editText(id: Long, text: String) =
        if (text.isBlank()) copy(texts = texts.filterNot { it.id == id })
        else copy(texts = texts.map { if (it.id == id) it.copy(text = text) else it })

    fun moveSticker(id: Long, x: Float, y: Float) =
        copy(stickers = stickers.map { if (it.id == id) it.copy(x = x, y = y) else it })

    fun removeSticker(id: Long) = copy(stickers = stickers.filterNot { it.id == id })

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

/** Text whose baseline starts at (x, y); [size] is the text height relative to the image width */
data class TextItem(
    val text: String,
    val x: Float,
    val y: Float,
    val size: Float = OverlayDefaults.TEXT_SIZE,
    val color: Int = OverlayDefaults.COLOR,
    /** Identifies the text when it is moved or edited */
    val id: Long = 0,
)

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
)

object OverlayDefaults {
    /** About as thick as the 5 px line on a typical phone screen before */
    const val STROKE_WIDTH = 0.006f
    const val TEXT_SIZE = 0.1f
    const val STICKER_WIDTH = 0.2f
    const val COLOR: Int = 0xFFFF0000.toInt()   // red
}

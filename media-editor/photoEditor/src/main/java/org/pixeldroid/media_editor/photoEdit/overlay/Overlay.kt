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
)

/** A sticker image centred at (x, y); [width] is relative to the image width, the height follows its aspect ratio */
data class StickerItem(
    val uri: String,
    val x: Float,
    val y: Float,
    val width: Float = OverlayDefaults.STICKER_WIDTH,
)

object OverlayDefaults {
    /** About as thick as the 5 px line on a typical phone screen before */
    const val STROKE_WIDTH = 0.006f
    const val TEXT_SIZE = 0.1f
    const val STICKER_WIDTH = 0.2f
    const val COLOR: Int = 0xFFFF0000.toInt()   // red
}

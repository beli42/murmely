package org.pixeldroid.media_editor.photoEdit.overlay

/**
 * Which text or sticker is at a point, for moving and editing. Everything in image-relative
 * coordinates; the image's aspect ratio is needed because sizes are relative to the image width.
 * Pure, no Android dependencies.
 */
object OverlayHitTest {

    /** Extra margin around texts and stickers, relative to the image width, so they are easy to grab */
    const val MARGIN = 0.02f

    data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        operator fun contains(p: RelativePoint) = p.x in left..right && p.y in top..bottom
    }

    /** Area of a sticker (not turned); [imageAspect] = image height / image width */
    fun stickerBounds(sticker: StickerItem, imageAspect: Float): Bounds {
        val halfWidth = sticker.width / 2
        val halfHeight = sticker.width * sticker.aspect / imageAspect / 2
        return Bounds(sticker.x - halfWidth, sticker.y - halfHeight, sticker.x + halfWidth, sticker.y + halfHeight)
    }

    /**
     * Area of a text: from its baseline up by its size, and as wide as [textWidth] (relative to
     * the image width, measured with the real font by the caller).
     */
    fun textBounds(text: TextItem, textWidth: Float, imageAspect: Float): Bounds {
        val height = text.size / imageAspect
        return Bounds(text.x, text.y - height, text.x + textWidth, text.y + height * 0.25f)
    }

    /** The topmost (last added) sticker at [point], or null; turned stickers are hit where they are */
    fun stickerAt(overlay: Overlay, point: RelativePoint, imageAspect: Float): StickerItem? =
        overlay.stickers.lastOrNull { StickerGeometry.contains(it, point, imageAspect, MARGIN) }

    /** The topmost text at [point], or null; [textWidth] measures a text relative to the image width */
    fun textAt(overlay: Overlay, point: RelativePoint, imageAspect: Float, textWidth: (TextItem) -> Float): TextItem? =
        overlay.texts.lastOrNull { point in textBounds(it, textWidth(it), imageAspect).grown(MARGIN, imageAspect) }

    private fun Bounds.grown(margin: Float, imageAspect: Float) =
        Bounds(left - margin, top - margin / imageAspect, right + margin, bottom + margin / imageAspect)
}

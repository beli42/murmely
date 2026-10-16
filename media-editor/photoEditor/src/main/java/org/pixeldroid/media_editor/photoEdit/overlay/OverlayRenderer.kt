package org.pixeldroid.media_editor.photoEdit.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.withRotation
import androidx.core.net.toUri
import org.pixeldroid.media_editor.photoEdit.getBitmap

/**
 * Draws an [Overlay] onto a canvas of the image's size. Used for the on-screen preview (canvas
 * the size of the shown image) and for the saved file (canvas the size of the full image), so
 * both look the same.
 */
object OverlayRenderer {

    /**
     * @param stickers loaded sticker images by sticker id (see [loadStickers]); missing ones are skipped
     * @param inProgress a stroke that is still being drawn, shown on top
     */
    fun draw(
        canvas: Canvas,
        width: Int,
        height: Int,
        overlay: Overlay,
        stickers: Map<Long, Bitmap> = emptyMap(),
        inProgress: Stroke? = null,
    ) {
        (overlay.strokes + listOfNotNull(inProgress)).forEach { drawStroke(canvas, width, height, it) }
        overlay.texts.forEach { drawText(canvas, width, height, it) }
        overlay.stickers.forEach { sticker -> stickers[sticker.id]?.let { drawSticker(canvas, width, height, sticker, it) } }
    }

    private fun drawStroke(canvas: Canvas, width: Int, height: Int, stroke: Stroke) {
        if (stroke.points.isEmpty()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = stroke.color
            style = Paint.Style.STROKE
            strokeWidth = stroke.width * width
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val first = stroke.points.first()
        if (stroke.points.size == 1) {
            // A tap: a dot
            canvas.drawPoint(first.x * width, first.y * height, paint)
            return
        }
        val path = Path().apply {
            moveTo(first.x * width, first.y * height)
            stroke.points.drop(1).forEach { lineTo(it.x * width, it.y * height) }
        }
        canvas.drawPath(path, paint)
    }

    /** The paint for [text] on an image [imageWidth] pixels wide (also for measuring it) */
    fun textPaint(text: TextItem, imageWidth: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = text.color
        style = Paint.Style.FILL
        textSize = text.size * imageWidth
        textAlign = Paint.Align.CENTER
        typeface = TextTypefaces.typeface(text.font)
    }

    /** Width of [text] relative to the image width */
    fun textWidth(text: TextItem): Float {
        // Measured at a fixed size: the width is proportional to the size
        val paint = textPaint(text.copy(size = 1f), MEASURE_WIDTH)
        return paint.measureText(text.text) / MEASURE_WIDTH * text.size
    }

    private const val MEASURE_WIDTH = 1000

    private fun drawText(canvas: Canvas, width: Int, height: Int, text: TextItem) {
        val paint = textPaint(text, width)
        val centreX = text.x * width
        val centreY = text.y * height
        // Baseline so that the line (ascent to descent) is centred on (x, y)
        val metrics = paint.fontMetrics
        val baseline = centreY - (metrics.ascent + metrics.descent) / 2
        canvas.withRotation(text.rotation, centreX, centreY) {
            drawText(text.text, centreX, baseline, paint)
        }
    }

    private fun drawSticker(canvas: Canvas, width: Int, height: Int, sticker: StickerItem, bitmap: Bitmap) {
        val stickerWidth = sticker.width * width
        val stickerHeight = stickerWidth * bitmap.height / bitmap.width
        val centreX = sticker.x * width
        val centreY = sticker.y * height
        val target = RectF(
            centreX - stickerWidth / 2, centreY - stickerHeight / 2,
            centreX + stickerWidth / 2, centreY + stickerHeight / 2,
        )
        canvas.withRotation(sticker.rotation, centreX, centreY) {
            drawBitmap(bitmap, null, target, Paint(Paint.FILTER_BITMAP_FLAG))
        }
    }

    /**
     * Loads the sticker images at the size they get on an image [imageWidth] pixels wide, by
     * sticker id: the same image can be used by several stickers of different sizes
     */
    suspend fun loadStickers(context: Context, overlay: Overlay, imageWidth: Int): Map<Long, Bitmap> =
        overlay.stickers.associate { sticker ->
            val stickerWidth = (sticker.width * imageWidth).toInt().coerceAtLeast(1)
            // fit = keep the aspect ratio; the height limit is generous so the width decides
            sticker.id to getBitmap(context, sticker.uri.toUri(), stickerWidth, stickerWidth * 8, fit = true)
        }.mapNotNull { (id, bitmap) -> bitmap?.let { id to it } }.toMap()
}

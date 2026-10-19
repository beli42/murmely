package org.pixeldroid.media_editor.photoEdit.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.withRotation
import kotlin.math.cos
import kotlin.math.sin
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
        // Texts and stickers from back to front
        val texts = overlay.texts.associateBy { it.id }
        val stickerItems = overlay.stickers.associateBy { it.id }
        overlay.layerOrder.forEach { id ->
            texts[id]?.let { drawText(canvas, width, height, it) }
            stickerItems[id]?.let { sticker -> stickers[sticker.id]?.let { drawSticker(canvas, width, height, sticker, it) } }
        }
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
        // In ems, like the effect's value: measured widths include it
        letterSpacing = text.effects.letterSpacing
    }

    /** Width of [text] relative to the image width */
    fun textWidth(text: TextItem): Float {
        // Measured at a fixed size: the width is proportional to the size
        val paint = textPaint(text.copy(size = 1f), MEASURE_WIDTH)
        return paint.measureText(text.text) / MEASURE_WIDTH * text.size
    }

    private const val MEASURE_WIDTH = 1000

    private fun drawText(canvas: Canvas, width: Int, height: Int, text: TextItem) {
        val centreX = text.x * width
        val centreY = text.y * height
        canvas.withRotation(text.rotation, centreX, centreY) {
            drawTextAt(this, text, text.size * width, centreX, centreY)
        }
    }

    /**
     * [text] (its words, font, colour and effects) with its line centred on ([centreX],
     * [centreY]) at [textSize] pixels. Drop shadow first, then the outline (carrying the soft
     * shadow), then the letters: the outline lies behind them, so they don't get thinner.
     * Also used for the text preview ("Murmely") in the text settings.
     */
    fun drawTextAt(canvas: Canvas, text: TextItem, textSize: Float, centreX: Float, centreY: Float) {
        val e = text.effects
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = text.color
            style = Paint.Style.FILL
            this.textSize = textSize
            textAlign = Paint.Align.CENTER
            typeface = TextTypefaces.typeface(text.font)
            letterSpacing = e.letterSpacing
        }
        // Baseline so that the line (ascent to descent) is centred on the point
        val metrics = fill.fontMetrics
        val baseline = centreY - (metrics.ascent + metrics.descent) / 2
        val outlineWidth = e.outline * textSize
        val outline = if (outlineWidth > 0f) Paint(fill).apply {
            color = e.outlineColor
            style = Paint.Style.STROKE
            // The stroke is centred on the letter's edge: twice as wide shows the width outside
            strokeWidth = outlineWidth * 2
            strokeJoin = Paint.Join.ROUND
        } else null

        if (e.dropShadow > 0f) {
            val angle = Math.toRadians(e.dropShadowAngle.toDouble())
            val dx = (cos(angle) * e.dropShadow * textSize).toFloat()
            val dy = (sin(angle) * e.dropShadow * textSize).toFloat()
            val drop = Paint(fill).apply { color = e.dropShadowColor }
            outline?.let { canvas.drawText(text.text, centreX + dx, baseline + dy, Paint(it).apply { color = e.dropShadowColor }) }
            canvas.drawText(text.text, centreX + dx, baseline + dy, drop)
        }
        // The soft shadow goes with the outermost shape: the outline if there is one
        val shadowed = outline ?: fill
        if (e.shadow > 0f) {
            shadowed.setShadowLayer(
                e.shadow * TextEffects.SHADOW_BLUR * textSize,
                e.shadow * TextEffects.SHADOW_OFFSET * textSize,
                e.shadow * TextEffects.SHADOW_OFFSET * textSize,
                SHADOW_COLOR,
            )
        }
        outline?.let { canvas.drawText(text.text, centreX, baseline, it) }
        canvas.drawText(text.text, centreX, baseline, fill)
    }

    /** Black at half opacity */
    private const val SHADOW_COLOR = 0x80000000.toInt()

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

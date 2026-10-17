package org.pixeldroid.media_editor.photoEdit.frame

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Draws frames. The same drawing is used for the preview, the template previews and the saved
 * image, so they look alike.
 */
object FrameRenderer {

    /**
     * [photo] with the frame: drawn over its edge (inside, [photo] itself is changed) or a new,
     * larger bitmap with the photo in the middle (outside)
     */
    fun apply(photo: Bitmap, settings: FrameSettings): Bitmap {
        if (settings.placement == FramePlacement.INSIDE) {
            draw(Canvas(photo), settings, photo.width, photo.height)
            return photo
        }
        val (width, height) = FrameGeometry.outputSize(settings, photo.width, photo.height)
        val m = FrameGeometry.margins(settings, photo.width, photo.height)
        val framed = createBitmap(width, height)
        val canvas = Canvas(framed)
        canvas.drawBitmap(photo, m.left.toFloat(), m.top.toFloat(), null)
        draw(canvas, settings, photo.width, photo.height)
        return framed
    }

    /**
     * The frame for a photo of [photoWidth] x [photoHeight] on [canvas], in pixels of the result
     * (scale the canvas to draw it smaller); only the frame, the photo shows through its window
     */
    fun draw(canvas: Canvas, settings: FrameSettings, photoWidth: Int, photoHeight: Int) {
        val (width, height) = FrameGeometry.outputSize(settings, photoWidth, photoHeight)
        val w = FrameGeometry.window(settings, photoWidth, photoHeight)
        val window = RectF(w.left.toFloat(), w.top.toFloat(), (w.left + w.width).toFloat(), (w.top + w.height).toFloat())
        val outer = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val base = FrameGeometry.base(settings, photoWidth, photoHeight)
        val color = settings.color
        val contrast = contrastTo(color)

        // The frame area: everything outside the window (rounded for ROUNDED)
        val area = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addRect(outer, Path.Direction.CW)
            if (settings.style == FrameStyle.ROUNDED) {
                val radius = min(base * 1.2f, min(window.width(), window.height()) / 2)
                addRoundRect(window, radius, radius, Path.Direction.CW)
            } else {
                addRect(window, Path.Direction.CW)
            }
        }
        canvas.drawPath(area, fill(color))

        when (settings.style) {
            FrameStyle.DOUBLE_LINE -> {
                val line = stroke(contrast, max(1f, base * 0.07f))
                for (inset in listOf(0.3f, 0.65f)) {
                    canvas.drawRect(inset(outer, base * inset), line)
                }
            }
            FrameStyle.PASSEPARTOUT -> {
                // Fine line just outside the photo, like the cut edge of a mat
                val line = stroke(withAlpha(contrast, 0x8C), max(1f, base * 0.06f))
                canvas.drawRect(inset(window, -base * 0.12f), line)
            }
            FrameStyle.FILM -> drawPerforation(canvas, width.toFloat(), height.toFloat(), window, contrast)
            else -> Unit
        }
    }

    /** Holes in the bands above and below the photo */
    private fun drawPerforation(canvas: Canvas, width: Float, height: Float, window: RectF, color: Int) {
        val paint = fill(withAlpha(color, 0xE0))
        for ((top, bottom) in listOf(0f to window.top, window.bottom to height)) {
            val band = bottom - top
            if (band <= 2f) continue
            val holeHeight = band * 0.45f
            val holeWidth = holeHeight * 1.3f
            val step = holeWidth * 1.9f
            // Centred row of holes
            val count = max(1, floor(width / step).toInt())
            val start = (width - count * step) / 2 + (step - holeWidth) / 2
            val y = top + (band - holeHeight) / 2
            val radius = holeHeight * 0.2f
            for (i in 0 until count) {
                val x = start + i * step
                canvas.drawRoundRect(RectF(x, y, x + holeWidth, y + holeHeight), radius, radius, paint)
            }
        }
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }

    private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = width
    }

    private fun inset(rect: RectF, by: Float) = RectF(rect.left + by, rect.top + by, rect.right - by, rect.bottom - by)

    /** Black on light colours, white on dark ones */
    private fun contrastTo(color: Int): Int {
        val red = (color shr 16) and 0xFF
        val green = (color shr 8) and 0xFF
        val blue = color and 0xFF
        val luminance = (0.299 * red + 0.587 * green + 0.114 * blue) / 255
        return if (luminance > 0.5) Color.BLACK else Color.WHITE
    }

    private fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha shl 24)
}

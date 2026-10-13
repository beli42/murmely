package org.pixeldroid.media_editor.photoEdit

import android.content.Context
import androidx.core.graphics.createBitmap
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri

/** Draws a collage into a bitmap. Separate from the UI so it can be tested on its own. */
object CollageRenderer {

    const val SIZE = 2000

    /**
     * @param images image number (from 1) to image
     * @param layout see [CollageGeometry]
     */
    suspend fun render(
        context: Context,
        images: Map<Int, Uri>,
        layout: Int,
        mode: CollageScaleMode,
        width: Int = SIZE,
        height: Int = SIZE,
    ): Bitmap {
        val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val white = Paint().apply { color = Color.WHITE }
        val smooth = Paint(Paint.FILTER_BITMAP_FLAG)

        images.toSortedMap().forEach { (number, uri) ->
            val cell = CollageGeometry.cell(layout, number, width, height)
            // CROP: exactly the cell's size. FIT and STRETCH: whole image, as large as fits
            val image = getBitmap(
                context, uri, cell.width, cell.height, fit = mode != CollageScaleMode.CROP
            ) ?: return@forEach
            // Whole image: white bars around it (also covers what is behind a small inset image)
            if (mode == CollageScaleMode.FIT) {
                canvas.drawRect(Rect(cell.left, cell.top, cell.right, cell.bottom), white)
            }
            val p = CollageGeometry.placement(cell, image.width, image.height, mode)
            canvas.drawBitmap(image, null, RectF(p.left, p.top, p.right, p.bottom), smooth)
        }
        return bitmap
    }
}

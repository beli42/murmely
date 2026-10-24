package org.pixeldroid.media_editor.photoEdit.crop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import kotlin.math.roundToInt
import androidx.core.graphics.createBitmap
import android.graphics.Paint
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import org.pixeldroid.media_editor.photoEdit.SaveFormat
import org.pixeldroid.media_editor.photoEdit.bitmapFromUri
import org.pixeldroid.media_editor.photoEdit.writeBitmap
import java.io.File
import kotlin.math.max

/** Cropping and turning images (on a background thread: decoding takes a while) */
object CropRenderer {

    /**
     * Writes the crop of [source] (turned, then cut, see [CropSettings]) at full resolution as a
     * lossless PNG into [dir] and returns it. The EXIF orientation is applied, like in the editor.
     */
    fun render(context: Context, source: Uri, settings: CropSettings, dir: File): Uri {
        val full = bitmapFromUri(context.contentResolver, source)
        val cropped = if (settings.angle == 0f) {
            val (x, y, width, height) = CropGeometry.sourcePixels(settings, full.width, full.height)
            val turn = Matrix().apply { postRotate(90f * Math.floorMod(settings.quarterTurns, 4)) }
            Bitmap.createBitmap(full, x, y, width, height, turn, false).also { if (it !== full) full.recycle() }
        } else {
            // Turned, straightened (enlarged to fill its frame), then cut; at most two full
            // size images at a time
            val turned = turned(full, settings.quarterTurns)
            if (turned !== full) full.recycle()
            val straight = straightened(turned, settings.angle, settings.fill)
            turned.recycle()
            val r = settings.rect
            val x = (r.left * straight.width).roundToInt().coerceIn(0, straight.width - 1)
            val y = (r.top * straight.height).roundToInt().coerceIn(0, straight.height - 1)
            val width = (r.width * straight.width).roundToInt().coerceIn(1, straight.width - x)
            val height = (r.height * straight.height).roundToInt().coerceIn(1, straight.height - y)
            Bitmap.createBitmap(straight, x, y, width, height).also { if (it !== straight) straight.recycle() }
        }
        val file = File.createTempFile("crop", ".png", dir)
        file.outputStream().writeBitmap(cropped, SaveFormat.PNG)
        cropped.recycle()
        return Uri.fromFile(file)
    }

    /** [source] scaled down (by powers of two) to at most about [maxSide] pixels, for showing it */
    fun preview(context: Context, source: Uri, maxSide: Int): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, source)) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)
            var sample = 1
            while (longest / (sample * 2) >= maxSide) sample *= 2
            decoder.setTargetSampleSize(sample)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setOnPartialImageListener { true }
        }

    /**
     * [bitmap] turned by [degrees] around its middle. Filled ([StraightenFill.ZOOM]): enlarged
     * just enough to fill its own size again, no empty corners. Otherwise all of it in the box
     * around it (see CropGeometry.frameSize), the empty corners see-through or black.
     */
    fun straightened(bitmap: Bitmap, degrees: Float, fill: StraightenFill = StraightenFill.ZOOM): Bitmap {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val (frameW, frameH) = CropGeometry.frameSize(w, h, CropSettings(angle = degrees, fill = fill))
        val scale = if (fill == StraightenFill.ZOOM) CropGeometry.straightenScale(w, h, degrees) else 1f
        val result = createBitmap(frameW.roundToInt(), frameH.roundToInt())
        if (fill == StraightenFill.BLACK) result.eraseColor(Color.BLACK)
        val matrix = Matrix().apply {
            postTranslate(-w / 2, -h / 2)
            postScale(scale, scale)
            postRotate(degrees)
            postTranslate(result.width / 2f, result.height / 2f)
        }
        Canvas(result).drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return result
    }

    /** [bitmap] turned [quarterTurns] times 90° clockwise */
    fun turned(bitmap: Bitmap, quarterTurns: Int): Bitmap {
        val turns = Math.floorMod(quarterTurns, 4)
        if (turns == 0) return bitmap
        val matrix = Matrix().apply { postRotate(90f * turns) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}

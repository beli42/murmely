package org.pixeldroid.media_editor.photoEdit.crop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
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
        val (x, y, width, height) = CropGeometry.sourcePixels(settings, full.width, full.height)
        val turn = Matrix().apply { postRotate(90f * Math.floorMod(settings.quarterTurns, 4)) }
        val cropped = Bitmap.createBitmap(full, x, y, width, height, turn, false)
        if (cropped !== full) full.recycle()
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

    /** [bitmap] turned [quarterTurns] times 90° clockwise */
    fun turned(bitmap: Bitmap, quarterTurns: Int): Bitmap {
        val turns = Math.floorMod(quarterTurns, 4)
        if (turns == 0) return bitmap
        val matrix = Matrix().apply { postRotate(90f * turns) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}

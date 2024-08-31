package org.pixeldroid.media_editor.photoEdit

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.exifinterface.media.ExifInterface
import com.google.android.material.color.MaterialColors


fun bitmapFromUri(contentResolver: ContentResolver, uri: Uri): Bitmap =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(contentResolver, uri)
            )
            { decoder, _, _ -> decoder.isMutableRequired = true }
    } else {
        val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(uri))
        modifyOrientation(bitmap!!, contentResolver, uri)
    }

fun bitmapFromUri(
    contentResolver: ContentResolver,
    uri: Uri,
    reqWidth: Int,
): Pair<Bitmap?, Double> {
    try {
        // First decode with inJustDecodeBounds=true to check dimensions
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        contentResolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, options)
        }

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth)

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false
        return Pair(contentResolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, options)
        }, options.outWidth.toDouble()/options.outHeight)
    } catch (e: Exception) {
        e.printStackTrace()
        return Pair(null, 1.0)
    }
}


fun modifyOrientation(
    bitmap: Bitmap,
    contentResolver: ContentResolver,
    uri: Uri
): Bitmap {
    val inputStream = contentResolver.openInputStream(uri)!!
    val ei = ExifInterface(inputStream)
    return when (ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> bitmap.rotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> bitmap.rotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> bitmap.rotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> bitmap.flip(horizontal = true, vertical = false)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> bitmap.flip(horizontal = false, vertical = true)
        else -> bitmap
    }
}

fun Bitmap.rotate(degrees: Float): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(degrees)
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

fun Bitmap.flip(horizontal: Boolean, vertical: Boolean): Bitmap {
    val matrix = Matrix()
    matrix.preScale(if (horizontal) -1f else 1f, if (vertical) -1f else 1f)
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

@ColorInt
fun Context.getColorFromAttr(@AttrRes attrColor: Int): Int = MaterialColors.getColor(this, attrColor, Color.BLACK)

fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int): Int {
    // Raw width of image
    val width = options.outWidth
    var inSampleSize = 1

    if (width > reqWidth) {
        val halfWidth = width / 2

        // Calculate the largest inSampleSize value that is a power of 2 and keeps
        // width larger than the requested width.
        while ((halfWidth / inSampleSize) >= reqWidth) {
            inSampleSize *= 2
        }
    }

    return inSampleSize
}
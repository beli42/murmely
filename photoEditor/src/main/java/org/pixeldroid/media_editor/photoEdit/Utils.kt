package org.pixeldroid.media_editor.photoEdit

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.exifinterface.media.ExifInterface
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.suspendCancellableCoroutine
import org.pixeldroid.media_editor.common.PICTURE_POSITION
import org.pixeldroid.media_editor.common.PICTURE_URI
import java.io.OutputStream
import kotlin.coroutines.resume


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

suspend fun getBitmap(context: Context, uri: Uri, width: Int, height: Int): Bitmap? = suspendCancellableCoroutine { cont ->
    Glide.with(context)
        .asBitmap()
        .load(uri).centerCrop()
        .override(width, height)
        .into(object : CustomTarget<Bitmap?>() {
            override fun onResourceReady(
                resource: Bitmap,
                transition: Transition<in Bitmap?>?
            ) {
                cont.resume(resource)
            }

            override fun onLoadCleared(placeholder: Drawable?) {
            }
        })
}


fun Activity.sendBackImage(file: String, picturePosition: Int?) {
    val intent = Intent()
        .apply {
            putExtra(PICTURE_URI, file)
            putExtra(PICTURE_POSITION, picturePosition)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }

    setResult(Activity.RESULT_OK, intent)
    finish()
}

fun OutputStream.writeBitmap(bitmap: Bitmap) {
    use { out ->
        //(quality is ignored for PNG)
        bitmap.compress(Bitmap.CompressFormat.PNG, 85, out)
        out.flush()
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
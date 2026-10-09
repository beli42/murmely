package org.pixeldroid.media_editor.photoEdit

import android.app.Activity
import androidx.core.graphics.createBitmap
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
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
    // ImageDecoder also applies the EXIF orientation
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, _, _ ->
        decoder.isMutableRequired = true
        // Show what is there of an incomplete/truncated file (rest is grey) instead of throwing
        decoder.setOnPartialImageListener { true }
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

/**
 * Loads [uri] scaled for a [width] x [height] area: with [fit] the whole image is kept (result may
 * be smaller than the area in one direction), otherwise it is cropped to fill the area exactly.
 */
suspend fun getBitmap(context: Context, uri: Uri, width: Int, height: Int, fit: Boolean = false): Bitmap? = suspendCancellableCoroutine { cont ->
    Glide.with(context)
        .asBitmap()
        .load(uri)
        .let { if (fit) it.fitCenter() else it.centerCrop() }
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

fun OutputStream.writeBitmap(
    bitmap: Bitmap,
    format: SaveFormat = SaveFormat.PNG,
    quality: Int = SaveFormat.DEFAULT_JPEG_QUALITY,
) {
    use { out ->
        if (format == SaveFormat.JPEG && bitmap.hasAlpha()) {
            // JPEG has no transparency: put transparent areas (e.g. empty collage parts) on
            // white instead of letting them turn black
            val opaque = createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(opaque).apply {
                drawColor(android.graphics.Color.WHITE)
                drawBitmap(bitmap, 0f, 0f, null)
            }
            opaque.compress(Bitmap.CompressFormat.JPEG, quality, out)
            opaque.recycle()
        } else {
            //(quality is ignored for PNG)
            bitmap.compress(format.compressFormat, quality, out)
        }
        out.flush()
    }
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
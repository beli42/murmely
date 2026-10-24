package org.pixeldroid.media_editor.photoEdit.info

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * Reads [ImageInfo] of an image file (on a background thread: it opens the file). The framework's
 * ExifInterface is reliable from Android 7 on; Murmely needs Android 10, so no extra library. Named
 * in full here (no import), so the reasoned exception above covers every use of it.
 */
@SuppressLint("ExifInterface")
object ImageInfoReader {

    /** Stops decoding once the header is read: only its information is needed */
    private class HeaderRead : RuntimeException()

    fun read(context: Context, uri: Uri): ImageInfo {
        val resolver = context.contentResolver
        var name: String? = null
        var bytes: Long? = null
        if (uri.scheme == "file") {
            uri.path?.let { File(it) }?.let { name = it.name; bytes = it.length() }
        } else {
            runCatching {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        name = c.getString(0)
                        bytes = if (c.isNull(1)) null else c.getLong(1)
                    }
                }
            }
        }
        var width: Int? = null
        var height: Int? = null
        var mime: String? = null
        var colorSpace: String? = null
        runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { _, info, _ ->
                width = info.size.width
                height = info.size.height
                mime = info.mimeType
                colorSpace = info.colorSpace?.name
                throw HeaderRead()
            }
        }
        val exif = runCatching { resolver.openInputStream(uri)?.use { android.media.ExifInterface(it) } }.getOrNull()
        // The orientation turns the pixels: shown the way the photo is seen
        val turned = exif?.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL) in
            setOf(android.media.ExifInterface.ORIENTATION_ROTATE_90, android.media.ExifInterface.ORIENTATION_ROTATE_270, android.media.ExifInterface.ORIENTATION_TRANSPOSE, android.media.ExifInterface.ORIENTATION_TRANSVERSE)
        if (turned) width = height.also { height = width }
        return ImageInfo(
            name = name,
            bytes = bytes,
            format = mime?.let { ImageInfoText.format(it) },
            width = width,
            height = height,
            colorSpace = colorSpace,
            taken = (exif?.getAttribute(android.media.ExifInterface.TAG_DATETIME_ORIGINAL) ?: exif?.getAttribute(android.media.ExifInterface.TAG_DATETIME))?.let { ImageInfoText.taken(it) },
            camera = ImageInfoText.camera(exif?.getAttribute(android.media.ExifInterface.TAG_MAKE), exif?.getAttribute(android.media.ExifInterface.TAG_MODEL)),
        )
    }
}

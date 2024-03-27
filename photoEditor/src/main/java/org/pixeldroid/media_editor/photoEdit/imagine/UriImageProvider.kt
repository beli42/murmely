package org.pixeldroid.media_editor.photoEdit.imagine

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import org.pixeldroid.media_editor.photoEdit.bitmapFromUri
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineImageProvider

class UriImageProvider(
    private val context: Context,
    private val uri: Uri,
) : ImagineImageProvider {
    override val bitmap: Bitmap
        get() = bitmapFromUri(context.contentResolver, uri)
}
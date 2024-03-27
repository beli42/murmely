package org.pixeldroid.media_editor.photoEdit.imagine.core.types

import android.graphics.Bitmap

/**
 * Abstracts a bitmap provider
 */
interface ImagineImageProvider {
    /**
     * A valid bitmap representing the image
     */
    val bitmap: Bitmap
}
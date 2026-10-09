package org.pixeldroid.media_editor.photoEdit

/** File format for saved images */
enum class SaveFormat(val mimeType: String, val extension: String) {
    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg");

    // A getter, so the enum itself has no Android dependency (unit tests)
    val compressFormat: android.graphics.Bitmap.CompressFormat
        get() = when (this) {
            PNG -> android.graphics.Bitmap.CompressFormat.PNG
            JPEG -> android.graphics.Bitmap.CompressFormat.JPEG
        }

    companion object {
        const val DEFAULT_JPEG_QUALITY = 93

        /** "jpeg" / "jpg" (any case) -> JPEG, everything else -> PNG */
        fun fromName(name: String?): SaveFormat =
            if (name.equals("jpeg", true) || name.equals("jpg", true)) JPEG else PNG
    }
}

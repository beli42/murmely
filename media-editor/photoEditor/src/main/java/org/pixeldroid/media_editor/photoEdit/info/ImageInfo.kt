package org.pixeldroid.media_editor.photoEdit.info

import kotlin.math.roundToInt

/**
 * What is known about an image file: where it is, name and size of the file, format, pixels, colour space,
 * and from its EXIF data when it has them, when it was taken and with which camera
 */
data class ImageInfo(
    /** Full path of the file, if known */
    val path: String?,
    val name: String?,
    val bytes: Long?,
    val format: String?,
    val width: Int?,
    val height: Int?,
    val colorSpace: String?,
    val taken: String?,
    val camera: String?,
)

/** Formats image information for showing. Pure, no Android. */
object ImageInfoText {

    /** The folder a file is in: "/storage/emulated/0/DCIM/Camera/x.jpg" → "/storage/emulated/0/DCIM/Camera" */
    fun folder(path: String): String = path.substringBeforeLast('/').ifEmpty { "/" }

    /** "820 KB", "2.4 MB" (1 MB = 1000 KB, like the system shows file sizes) */
    fun fileSize(bytes: Long): String = when {
        bytes < 1_000 -> "$bytes B"
        bytes < 1_000_000 -> "${(bytes / 1_000.0).roundToInt()} KB"
        else -> "${(bytes / 100_000.0).roundToInt() / 10.0} MB"
    }

    /** "4000 × 3000 (12 MP)"; under a megapixel without it */
    fun resolution(width: Int, height: Int): String {
        val megapixels = width.toLong() * height / 1_000_000.0
        return if (megapixels < 1) "$width × $height" else "$width × $height (${(megapixels * 10).roundToInt() / 10.0} MP)"
    }

    /** "image/jpeg" → "JPEG", "image/webp" → "WEBP" */
    fun format(mime: String): String = mime.substringAfter('/').uppercase()

    /** EXIF "2024:05:01 12:34:56" → "2024-05-01 12:34"; anything else as it is */
    fun taken(exif: String): String {
        val match = Regex("""(\d{4}):(\d{2}):(\d{2}) (\d{2}):(\d{2})""").find(exif) ?: return exif
        val (y, mo, d, h, mi) = match.destructured
        return "$y-$mo-$d $h:$mi"
    }

    /** Make and model, without the make repeated at the start of the model ("Google Pixel 7") */
    fun camera(make: String?, model: String?): String? {
        val m = make?.trim().orEmpty()
        val mo = model?.trim().orEmpty()
        return when {
            m.isEmpty() && mo.isEmpty() -> null
            m.isEmpty() -> mo
            mo.isEmpty() -> m
            mo.startsWith(m, ignoreCase = true) -> mo
            else -> "$m $mo"
        }
    }
}

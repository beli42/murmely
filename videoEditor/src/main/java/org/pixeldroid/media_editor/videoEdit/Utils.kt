package org.pixeldroid.media_editor.videoEdit

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.arthenica.ffmpegkit.FFmpegKitConfig


fun Context.ffmpegCompliantReadUri(inputUri: Uri?): String =
    if (inputUri?.scheme == "content")
        FFmpegKitConfig.getSafParameterForRead(this, inputUri)
    else inputUri.toString()

fun Context.ffmpegCompliantWriteUri(inputUri: Uri?): String =
    if (inputUri?.scheme == "content")
        FFmpegKitConfig.getSafParameterForWrite(this, inputUri)
    else inputUri.toString()

/** Maps a Float from this range to target range */
fun ClosedRange<Float>.convert(number: Float, target: ClosedRange<Float>): Float {
    val ratio = number / (endInclusive - start)
    return (ratio * (target.endInclusive - target.start))
}

fun Uri.fileExtension(contentResolver: ContentResolver): String? {
    return if (scheme == "content") {
        contentResolver.getType(this)?.takeLastWhile { it != '/' }
    } else {
        MimeTypeMap.getFileExtensionFromUrl(toString()).ifEmpty { null }
    }
}
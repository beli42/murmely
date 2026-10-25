package org.pixeldroid.media_editor.videoEdit

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri

/** Length of a video in seconds, or null if it can't be read */
fun Context.videoDurationSeconds(uri: Uri): Float? = try {
    MediaMetadataRetriever().use { retriever ->
        retriever.setDataSource(this, uri)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.div(1000f)
    }
} catch (e: Exception) {
    null
}

/** Height / width of a video as it is shown (turned by its rotation), or null if it can't be read */
fun Context.videoAspect(uri: Uri): Float? = try {
    MediaMetadataRetriever().use { retriever ->
        retriever.setDataSource(this, uri)
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull()
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull()
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        if (width == null || height == null || width <= 0f || height <= 0f) null
        else if (rotation % 180 != 0) width / height else height / width
    }
} catch (e: Exception) {
    null
}

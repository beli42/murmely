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

package org.pixeldroid.media_editor.videoEdit

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File
import kotlin.concurrent.thread

/**
 * Converts videos with Media3 Transformer (hardware codecs, no native code of our own).
 *
 * The result is first written to a temporary file and only copied to the target when the
 * conversion succeeded. If it fails or is cancelled, the target (already created by the app, e.g.
 * through the system's "save as" dialog) is deleted, so no empty or broken file is left behind.
 * If the requested format is not supported by the device's encoder (e.g. 4K on a device that can
 * only encode 1080p), Transformer falls back to a supported one.
 */
@OptIn(UnstableApi::class)
object VideoEncoder {

    private const val TAG = "VideoEncoder"
    private const val PROGRESS_INTERVAL_MS = 500L

    private class Session(
        val context: Context,
        val transformer: Transformer,
        val target: Uri?,
        val tempFile: File,
    )

    private val sessions = HashMap<Long, Session>()
    private var nextSessionId = 1L
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Starts converting [originalUri]. Callbacks are called on the main thread.
     *
     * @param targetUri where to save the result; if null, it stays in a temporary file
     * @param registerSession receives the id to cancel this conversion with [cancel]
     * @param trackTempFile receives temporary files, to be deleted by the caller later
     * @param progress (original, percent, firstPass, output, error): output is set when done or
     * failed, error is true when it failed. firstPass is always false (there is no first pass).
     */
    @MainThread
    fun start(
        context: Context,
        originalUri: Uri,
        targetUri: Uri?,
        arguments: VideoEditArguments,
        registerSession: (Uri, Long) -> Unit,
        trackTempFile: (File) -> Unit,
        progress: (Uri, Int, Boolean, Uri?, Boolean) -> Unit,
    ) {
        val appContext = context.applicationContext
        val id = nextSessionId++
        val tempFile = File.createTempFile("bunny_video_", ".mp4", appContext.cacheDir)
        trackTempFile(tempFile)

        val transformer = Transformer.Builder(appContext)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    completed(id, originalUri, progress)
                }

                override fun onError(
                    composition: Composition, exportResult: ExportResult, exportException: ExportException,
                ) {
                    Log.e(TAG, "Conversion failed", exportException)
                    failed(id, originalUri, progress)
                }
            })
            .build()

        sessions[id] = Session(appContext, transformer, targetUri, tempFile)
        registerSession(originalUri, id)
        transformer.start(editedMediaItem(originalUri, arguments), tempFile.absolutePath)
        reportProgress(id, originalUri, progress)
    }

    /** Stops a conversion and removes its files */
    @MainThread
    fun cancel(sessionId: Long) {
        val session = sessions.remove(sessionId) ?: return
        session.transformer.cancel()
        session.tempFile.delete()
        session.target?.let { deleteFile(session.context, it) }
    }

    @MainThread
    fun cancelAll() = sessions.keys.toList().forEach(::cancel)

    /** The source with everything from [arguments] applied */
    fun editedMediaItem(originalUri: Uri, arguments: VideoEditArguments): EditedMediaItem {
        val (startMs, endMs) = VideoEditPlan.clippingMs(arguments)
        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(startMs)
            .apply { endMs?.let { setEndPositionMs(it) } }
            .build()
        val mediaItem = MediaItem.Builder().setUri(originalUri).setClippingConfiguration(clipping).build()

        val videoEffects: List<Effect> =
            if (arguments.videoCrop.notCropped()) emptyList()
            else VideoEditPlan.cropNdc(arguments.videoCrop).let { listOf(Crop(it.left, it.right, it.bottom, it.top)) }

        val speed = VideoEditPlan.speed(arguments)
        return EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(VideoEditPlan.removeAudio(arguments))
            .setEffects(Effects(emptyList(), videoEffects))
            .apply { if (speed != 1f) setSpeed(ConstantSpeed(speed)) }
            .build()
    }

    /** Same speed for the whole video */
    private class ConstantSpeed(private val speed: Float) : SpeedProvider {
        override fun getSpeed(timeUs: Long): Float = speed
        override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
    }

    private fun reportProgress(id: Long, originalUri: Uri, progress: (Uri, Int, Boolean, Uri?, Boolean) -> Unit) {
        val holder = ProgressHolder()
        mainHandler.post(object : Runnable {
            override fun run() {
                val session = sessions[id] ?: return   // finished, failed or cancelled
                if (session.transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    progress(originalUri, holder.progress, false, null, false)
                }
                mainHandler.postDelayed(this, PROGRESS_INTERVAL_MS)
            }
        })
    }

    private fun completed(id: Long, originalUri: Uri, progress: (Uri, Int, Boolean, Uri?, Boolean) -> Unit) {
        val session = sessions.remove(id) ?: return
        val target = session.target
        if (target == null) {
            progress(originalUri, 100, false, session.tempFile.toUri(), false)
            return
        }
        // Copying can take a while for long videos: not on the main thread
        thread(name = "BunnyVideoCopy") {
            val copied = try {
                session.context.contentResolver.openOutputStream(target, "w")!!.use { output ->
                    session.tempFile.inputStream().use { it.copyTo(output) }
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Could not write $target", e)
                false
            }
            session.tempFile.delete()
            mainHandler.post {
                if (copied) progress(originalUri, 100, false, target, false)
                else {
                    deleteFile(session.context, target)
                    progress(originalUri, 0, false, target, true)
                }
            }
        }
    }

    private fun failed(id: Long, originalUri: Uri, progress: (Uri, Int, Boolean, Uri?, Boolean) -> Unit) {
        val session = sessions.remove(id) ?: return
        session.tempFile.delete()
        session.target?.let { deleteFile(session.context, it) }
        progress(originalUri, 0, false, session.target, true)
    }

    /** Removes a target file the app created before the conversion */
    private fun deleteFile(context: Context, uri: Uri) {
        try {
            when {
                uri.scheme == "file" -> uri.path?.let { File(it).delete() }
                DocumentsContract.isDocumentUri(context, uri) -> DocumentsContract.deleteDocument(context.contentResolver, uri)
                else -> context.contentResolver.delete(uri, null, null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete $uri", e)
        }
    }
}

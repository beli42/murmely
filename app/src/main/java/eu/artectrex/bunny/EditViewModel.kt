package eu.artectrex.bunny

import android.app.Application
import androidx.core.net.toUri
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import org.pixeldroid.media_editor.common.PICTURE_URI
import org.pixeldroid.media_editor.photoEdit.DirectSave
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.pixeldroid.media_editor.videoEdit.VideoEditActivity
import org.pixeldroid.media_editor.videoEdit.VideoEditArguments
import java.io.File
import java.util.Locale


/** Which screen of MainActivity is shown */
enum class Screen { START, VIDEO_PROGRESS }

/** Steps of converting an edited video */
enum class VideoPhase {
    /** Started, no progress reported yet */
    ANALYSING,
    ENCODING,
    FINISHED,
    FAILED;

    val isRunning get() = this == ANALYSING || this == ENCODING
}

data class VideoProgress(val phase: VideoPhase, val percent: Int = 0)

/** Everything the start screen and the video progress screen show or have to do */
data class EditFlowUiState(
    val screen: Screen = Screen.START,
    /** Progress of a video conversion, null if none was started */
    val video: VideoProgress? = null,
    /** MIME type of the file picked last */
    val type: String? = null,
    /** File picked to edit; the editor is opened for it once (then [EditViewModel.launchedEdit]) */
    val uriToEdit: Uri? = null,
    /** An edited video came back: ask where to save it (then [EditViewModel.launchedVideoCreation]) */
    val startVideoEncoding: Boolean = false,
    /** The original of the file being edited */
    val videoUri: Uri? = null,
    /** One-time message for the start screen after an image was saved */
    val savedMessage: String? = null,
)

class EditViewModel(
    application: Application,
    state: SavedStateHandle,
) : AndroidViewModel(application) {

    companion object {
        /** Creates the ViewModel with the extras of the starting Intent (files shared by other apps) */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                EditViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!, createSavedStateHandle())
            }
        }
    }

    private val _uiState: MutableStateFlow<EditFlowUiState> = MutableStateFlow(EditFlowUiState())

    val uiState: StateFlow<EditFlowUiState> = _uiState

    // Id of the running video conversion, to cancel it
    private var encodingSession: Long? = null

    private var videoEncodingArguments: VideoEditArguments? = null

    // Keep track of temporary files to delete them (avoids filling cache super fast with videos)
    private val tempFiles: java.util.ArrayList<File> = java.util.ArrayList()

    init {
        // Get Uri from state - passed through to the ViewModel from the Intent (shared by another app).
        // Only content:// Uris are accepted: a file:// Uri could point to Bunny's own private files,
        // which another app could otherwise get into the public gallery through Bunny.
        val uri: Uri? = state[Intent.EXTRA_STREAM]
        if (uri != null) {
            if (SaveLocationText.isAcceptedSharedScheme(uri.scheme)) filePickResult(uri)
            else Log.w("Murmely", "Ignored shared file with unsupported scheme: ${uri.scheme}")
        }
    }

    fun editResult(data: Intent) {
        // If you edited a video:
        val isVideo = _uiState.value.type?.startsWith("video/") ?: false
        if (isVideo) {
            val modified: Boolean = data.getBooleanExtra(VideoEditActivity.MODIFIED, false)
            if (modified) {
                videoEncodingArguments = IntentCompat.getSerializableExtra(
                    data, VideoEditActivity.VIDEO_ARGUMENTS_TAG, VideoEditArguments::class.java
                )

                // You need to track the encoding sessions and cancel them when needed.
                // Here, we cancel the previous session for this image, because we are about to start a new one
                encodingSession?.let { VideoEditActivity.cancelEncoding(it) }

                _uiState.update { currentUiState ->
                    currentUiState.copy(
                        startVideoEncoding = true
                    )
                }
            }
        }
        // Otherwise it was an image (photo or collage): the file was created in the library.
        // Stay on the start screen and show where it was saved.
        else {
            val resultUri = data.getStringExtra(PICTURE_URI)
            // The original file coming back means nothing new was saved
            if (resultUri != null && resultUri != _uiState.value.videoUri?.toString()) {
                _uiState.update { currentUiState ->
                    currentUiState.copy(savedMessage = savedImageMessage(data))
                }
            }
        }
    }

    /**
     * "Image saved as Pictures/Murmely/photo-1.png", using the file the editor actually wrote
     */
    private fun savedImageMessage(data: Intent): String {
        val application: Context = getApplication()
        val uri = data.getStringExtra(PICTURE_URI)?.toUri()
            ?: return application.getString(R.string.image_saved)
        val name = DirectSave.describe(application, uri)
            ?: return application.getString(R.string.image_saved)
        val size = DirectSave.fileSize(application, uri)
            ?: return application.getString(R.string.image_saved_as, name)
        return application.getString(
            R.string.image_saved_as_size, name, Formatter.formatShortFileSize(application, size)
        )
    }

    /** Stops a running video conversion, if any, and returns to the start screen */
    fun cancelVideoProcessing() {
        encodingSession?.let { VideoEditActivity.cancelEncoding(it) }
        encodingSession = null
        _uiState.update { it.copy(video = null, screen = Screen.START) }
    }

    /** Cancel the previous session and remember the new one */
    private fun registerSession(uri: Uri, sessionId: Long) {
        encodingSession?.let { VideoEditActivity.cancelEncoding(it) }
        encodingSession = sessionId
    }

    /**
     * Track temporary files. Here, they are just added to an ArrayList
     */
    private fun trackTempFile(file: File) {
        tempFiles.add(file)
    }

    /**
     * @param originalUri the Uri of the file you sent to be edited
     * @param progress percentage of (this pass of) encoding that is done
     * @param firstPass Whether this is a first pass before the conversion (not used since Media3)
     * @param outputVideoPath when not null, it means the encoding is done and the result is saved in this file
     * @param error is true when there has been an error during encoding.
     */
    private fun videoEncodeProgress(
        originalUri: Uri,
        progress: Int,
        firstPass: Boolean,
        outputVideoPath: Uri?,
        error: Boolean
    ) {
        val video = when {
            error -> VideoProgress(VideoPhase.FAILED)
            // A result file means the video is done
            outputVideoPath != null -> VideoProgress(VideoPhase.FINISHED, 100)
            firstPass -> VideoProgress(VideoPhase.ANALYSING, progress)
            else -> VideoProgress(VideoPhase.ENCODING, progress)
        }
        _uiState.update { it.copy(video = video) }
    }

    // TODO refactor to expose in library code instead of copying?
    fun Uri.getMimeType(contentResolver: ContentResolver, fallback: String = "image/*"): String {
        return if (scheme == "content") {
            contentResolver.getType(this)
        } else {
            MimeTypeMap.getFileExtensionFromUrl(toString())
                ?.run {
                    MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(lowercase(Locale.getDefault()))
                }
        } ?: fallback
    }

    fun filePickResult(uri: Uri?) {
        if (uri != null) {
            val application: Context = getApplication()
            val type = uri.getMimeType(application.contentResolver)
            _uiState.update { currentUiState ->
                currentUiState.copy(type = type, uriToEdit = uri)
            }
        } else {
            Log.d("PhotoPicker", "No media selected")
        }
    }

    /** A collage always produces an image, regardless of what was picked before */
    fun launchedCollage() {
        _uiState.update { currentUiState ->
            currentUiState.copy(type = "image/png", uriToEdit = null, startVideoEncoding = false, videoUri = null)
        }
    }

    fun savedMessageShown() {
        _uiState.update { currentUiState -> currentUiState.copy(savedMessage = null) }
    }

    fun launchedEdit() {
        _uiState.update { currentUiState ->
            currentUiState.copy(uriToEdit = null, startVideoEncoding = false, videoUri = currentUiState.uriToEdit)
        }

    }

    /** Back to the start screen (a running video conversion continues) */
    fun goToStart() {
        _uiState.update { it.copy(screen = Screen.START) }
    }

    fun encodeVideoTo(newFileUri: Uri) {
        videoEncodingArguments?.let { arguments ->
            _uiState.value.videoUri?.let { uri ->
                VideoEditActivity.startEncoding(
                    originalUri = uri, targetUri = newFileUri, arguments = arguments, context = getApplication(),
                    // Callback that will register new sessions as they are started.
                    registerSession = ::registerSession,
                    // Callback that will let you track temporary files as they are created, such as results of the video editing or files used to store video analysis results.
                    // Move or copy files you want to keep, then delete the rest to not leak space.
                    trackTempFile = ::trackTempFile,
                    // Callback used to inform you of progress in the video editing.
                    videoEncodeProgress = ::videoEncodeProgress
                )
                _uiState.update { it.copy(screen = Screen.VIDEO_PROGRESS, video = VideoProgress(VideoPhase.ANALYSING)) }
            }
        }
    }

    fun launchedVideoCreation() {
        _uiState.update { currentUiState ->
            currentUiState.copy(startVideoEncoding = false)
        }
    }
}

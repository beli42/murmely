package eu.artectrex.bunny

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import org.pixeldroid.media_editor.common.PICTURE_URI
import org.pixeldroid.media_editor.photoEdit.DirectSave
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.pixeldroid.media_editor.videoEdit.VideoEditActivity
import java.io.File
import java.util.Locale


// Models the UI state
data class EditFlowUiState(
    val videoEncodingProgress: Int? = null,
    val messageSecondFragment: String? = null,
    val type: String? = null,
    val uriToEdit: Uri? = null,
    val fragment: Int = 1,
    val error: Boolean = false,
    val startVideoEncoding: Boolean = false,
    val videoUri: Uri? = null
    )

class EditViewModel(
    application: Application,
    state: SavedStateHandle,
) : AndroidViewModel(application) {

    private val _uiState: MutableStateFlow<EditFlowUiState> = MutableStateFlow(EditFlowUiState())

    val uiState: StateFlow<EditFlowUiState> = _uiState

    // FFmpeg Session IDs key we can use to cancel the encoding
    private var encodingSession: Long? = null

    private var videoEncodingArguments: VideoEditActivity.VideoEditArguments? = null

    // Keep track of temporary files to delete them (avoids filling cache super fast with videos)
    private val tempFiles: java.util.ArrayList<File> = java.util.ArrayList()

    init {
        // Get Uri from state - passed through to the ViewModel from the Intent
        val uri: Uri? = state[Intent.EXTRA_STREAM]
        uri?.let {
            filePickResult(it)
        }
    }

    fun editResult(data: Intent) {
        // If you edited a video:
        val isVideo = _uiState.value.type?.startsWith("video/") ?: false
        if (isVideo) {
            val modified: Boolean = data.getBooleanExtra(VideoEditActivity.MODIFIED, false)
            if (modified) {
                videoEncodingArguments = data.getSerializableExtra(
                        VideoEditActivity.VIDEO_ARGUMENTS_TAG
                    ) as? VideoEditActivity.VideoEditArguments

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
        // Otherwise it was an image, and we are done (file was created in library)
        else {
            _uiState.update { currentUiState ->
                currentUiState.copy(fragment = 2, messageSecondFragment = savedImageMessage(data))
            }
            //TODO show "done" UI
        }
    }

    /**
     * "Image saved as Pictures/Bunny/photo-1.png", using the file the editor actually wrote
     */
    private fun savedImageMessage(data: Intent): String {
        val application: Context = getApplication()
        val uri = data.getStringExtra(PICTURE_URI)?.let { Uri.parse(it) }
        val name = uri?.let { DirectSave.describe(application, it) }
        return if (name != null) application.getString(R.string.image_saved_as, name)
        else application.getString(R.string.image_saved)
    }

    /**
     * Cancel previous session and save latest one
     */
    private fun registerNewFFmpegSession(uri: Uri, sessionId: Long) {
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
     * @param firstPass Whether this is the first pass (currently for analysis of video stabilization) or the second (and last) pass.
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
        if (outputVideoPath != null) {
            // If outputVideoPath is not null, it means the video is done and we can tell the user
            _uiState.update { currentUiState ->
                currentUiState.copy(messageSecondFragment = "Finished encoding!", videoEncodingProgress = 100)
            }
        } else {
            // Update progress of encoding
            _uiState.update { currentUiState ->
                currentUiState.copy(videoEncodingProgress = progress, error = error, messageSecondFragment = if(firstPass) "First pass before encoding" else "Encoding video at $progress%")
            }
        }
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

    fun launchedEdit() {
        _uiState.update { currentUiState ->
            currentUiState.copy(uriToEdit = null, startVideoEncoding = false, videoUri = currentUiState.uriToEdit)
        }

    }

    fun goToFirstFragment() {
        _uiState.update { currentUiState ->
            currentUiState.copy(fragment = 1)
        }
    }
    fun goToSecondFragment() {
        _uiState.update { currentUiState ->
            currentUiState.copy(fragment = 2)
        }
    }

    fun encodeVideoTo(newFileUri: Uri) {
        videoEncodingArguments?.let { arguments ->
            _uiState.value.videoUri?.let { uri ->
                VideoEditActivity.startEncoding(
                    originalUri = uri, targetUri = newFileUri, arguments = arguments, context = getApplication(),
                    // Callback that will register new sessions as they are started.
                    registerNewFFmpegSession = ::registerNewFFmpegSession,
                    // Callback that will let you track temporary files as they are created, such as results of the video editing or files used to store video analysis results.
                    // Move or copy files you want to keep, then delete the rest to not leak space.
                    trackTempFile = ::trackTempFile,
                    // Callback used to inform you of progress in the video editing.
                    videoEncodeProgress = ::videoEncodeProgress
                )
                goToSecondFragment()
            }
        }
    }

    fun launchedVideoCreation() {
        _uiState.update { currentUiState ->
            currentUiState.copy(startVideoEncoding = false)
        }
    }
}

class EditViewModelFactory(private val application: Application) :
    AbstractSavedStateViewModelFactory() {
    override fun <T : ViewModel> create(
        key: String, modelClass: Class<T>, handle: SavedStateHandle
    ): T {
        return modelClass.getConstructor(Application::class.java, SavedStateHandle::class.java)
            .newInstance(application, handle)
    }
}

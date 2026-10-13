package eu.artectrex.bunny.start

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.artectrex.bunny.EditViewModel
import eu.artectrex.bunny.R
import eu.artectrex.bunny.SaveSettings
import eu.artectrex.bunny.Screen
import kotlinx.coroutines.launch
import org.pixeldroid.media_editor.common.PICTURE_POSITION
import org.pixeldroid.media_editor.common.PICTURE_URI
import org.pixeldroid.media_editor.photoEdit.FileNaming
import org.pixeldroid.media_editor.photoEdit.PhotoEditActivity
import org.pixeldroid.media_editor.photoEdit.ui.main.CollageActivity
import org.pixeldroid.media_editor.videoEdit.VideoEditActivity

/**
 * The main flow of the app: start screen and video progress screen. Connects the [EditViewModel]
 * with the system pickers and the editors, which are started with their own activities.
 */
@Composable
fun BunnyApp(
    viewModel: EditViewModel,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onExit: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val editLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            viewModel.editResult(data)
        } else if (result.resultCode != Activity.RESULT_CANCELED) {
            Toast.makeText(context, R.string.edit_error, Toast.LENGTH_SHORT).show()
        }
    }
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.filePickResult(uri)
    }
    // Converted videos are always MP4
    val createVideo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
        uri?.let(viewModel::encodeVideoTo)
    }

    // One-time actions requested through the state; each is marked as done right away
    LaunchedEffect(state.uriToEdit) {
        val uri = state.uriToEdit ?: return@LaunchedEffect
        editLauncher.launch(SaveSettings.addTo(editorIntent(context, uri, state.type), context))
        viewModel.launchedEdit()
    }
    LaunchedEffect(state.startVideoEncoding) {
        if (!state.startVideoEncoding) return@LaunchedEffect
        // "<original name>-1.mp4", like edited photos
        createVideo.launch(FileNaming.numberedName(baseName(context, state.videoUri), emptySet(), "mp4"))
        viewModel.launchedVideoCreation()
    }
    LaunchedEffect(state.savedMessage) {
        val message = state.savedMessage ?: return@LaunchedEffect
        viewModel.savedMessageShown()
        // Own scope: clearing the message must not cancel showing it
        scope.launch { snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long) }
    }

    when (state.screen) {
        Screen.START -> StartContent(
            snackbarHostState = snackbarHostState,
            onEdit = {
                pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            },
            onCollage = {
                // Collages are saved with the folder and format from the settings
                editLauncher.launch(SaveSettings.addTo(Intent(context, CollageActivity::class.java), context))
                viewModel.launchedCollage()
            },
            onSettings = onSettings,
            onAbout = onAbout,
            onExit = onExit,
        )
        Screen.VIDEO_PROGRESS -> {
            BackHandler(onBack = viewModel::goToStart)
            VideoProgressContent(
                video = state.video,
                onBack = viewModel::goToStart,
                onCancel = viewModel::cancelVideoProcessing,
            )
        }
    }
}

/** File name of [uri] without extension, e.g. "clip" for clip.mp4 */
private fun baseName(context: Context, uri: Uri?): String {
    val name = uri?.let {
        try {
            context.contentResolver.query(it, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        } catch (e: Exception) {
            null
        }
    } ?: uri?.lastPathSegment ?: "video"
    return name.substringBeforeLast('.').ifBlank { "video" }
}

/** Photo or video editor for [uri], saving to a new file */
private fun editorIntent(context: Context, uri: Uri, type: String?): Intent {
    val editor = if (type?.startsWith("video/") == true) VideoEditActivity::class.java else PhotoEditActivity::class.java
    return Intent(context, editor)
        .putExtra(PICTURE_URI, uri)
        .putExtra(PICTURE_POSITION, 0)
        .putExtra(PhotoEditActivity.SAVE_TO_NEW_FILE, true)
}

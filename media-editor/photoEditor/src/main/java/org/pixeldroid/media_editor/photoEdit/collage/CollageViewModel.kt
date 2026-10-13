package org.pixeldroid.media_editor.photoEdit.collage

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pixeldroid.media_editor.photoEdit.CollageRenderer
import org.pixeldroid.media_editor.photoEdit.CollageScaleMode
import org.pixeldroid.media_editor.photoEdit.DirectSave
import org.pixeldroid.media_editor.photoEdit.FileNaming
import org.pixeldroid.media_editor.photoEdit.PhotoEditActivity
import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.SaveFormat
import org.pixeldroid.media_editor.photoEdit.writeBitmap
import java.io.IOException

/** Everything the collage screens show or have to do */
data class CollageUiState(
    /** Chosen layout (see CollageGeometry), or null while choosing it */
    val layout: Int? = null,
    /** Image area (from 1) to image */
    val images: Map<Int, Uri> = emptyMap(),
    val mode: CollageScaleMode = CollageScaleMode.STRETCH,
    /** The collage as it will be saved, smaller */
    val preview: Bitmap? = null,
    val saving: Boolean = false,
    /** One-time message (string resource) */
    @StringRes val message: Int? = null,
    /** File name to suggest in the "save as" dialog; the dialog is shown once */
    val saveAsName: String? = null,
    /** The saved collage; the screen closes and returns it */
    val result: Uri? = null,
) {
    val canSave get() = layout != null && images.isNotEmpty() && !saving
}

/**
 * Collage: choose a layout, pick an image per area, choose how images are fitted, save.
 * The save settings (folder, format, quality) come from the Intent, see [PhotoEditActivity].
 */
class CollageViewModel(private val app: Application, extras: SavedStateHandle) : AndroidViewModel(app) {

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CollageViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!, createSavedStateHandle())
            }
        }
        private const val PREVIEW_SIZE = 1024
        private const val TAG = "CollageViewModel"
    }

    val directSave = DirectSave(
        app,
        extras.get<String>(PhotoEditActivity.SAVE_TO_FOLDER),
        extras.get<String>(PhotoEditActivity.SAVE_TO_FOLDER_URI)?.toUri(),
        SaveFormat.fromName(extras.get<String>(PhotoEditActivity.SAVE_FORMAT)),
        (extras.get<Int>(PhotoEditActivity.SAVE_JPEG_QUALITY) ?: SaveFormat.DEFAULT_JPEG_QUALITY).coerceIn(1, 100),
    )

    private val _state = MutableStateFlow(CollageUiState())
    val state: StateFlow<CollageUiState> = _state.asStateFlow()

    /** Area the image picker was opened for */
    private var pickingFor: Int? = null
    private var previewJob: Job? = null

    fun chooseLayout(layout: Int) {
        _state.update { it.copy(layout = layout, images = emptyMap(), preview = null) }
    }

    /** Back from the images to the layout choice */
    fun backToLayouts() {
        previewJob?.cancel()
        _state.update { it.copy(layout = null, images = emptyMap(), preview = null) }
    }

    fun pickImageFor(area: Int) {
        pickingFor = area
    }

    fun imagePicked(uri: Uri?) {
        val area = pickingFor ?: return
        pickingFor = null
        if (uri == null) return
        _state.update { it.copy(images = it.images + (area to uri)) }
        renderPreview()
    }

    /** STRETCH -> FIT -> CROP -> STRETCH */
    fun cycleMode() {
        val mode = _state.value.mode.next()
        _state.update {
            it.copy(
                mode = mode,
                message = when (mode) {
                    CollageScaleMode.STRETCH -> R.string.collage_mode_stretch
                    CollageScaleMode.FIT -> R.string.collage_mode_fit
                    CollageScaleMode.CROP -> R.string.collage_mode_crop
                },
            )
        }
        renderPreview()
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    fun saveAsShown() = _state.update { it.copy(saveAsName = null) }

    /** collage-<date>.<ext> (or -1, -2, ...) in the configured folder, or "save as" without one */
    fun save() {
        if (!_state.value.canSave) return
        if (!directSave.isAvailable) {
            saveAs()
            return
        }
        val base = baseName()
        val target = directSave.create { used ->
            FileNaming.nameWithOptionalNumber(base, used, directSave.format.extension)
        }
        if (target == null) {
            // Folder deleted or permission revoked: let the user choose instead
            _state.update { it.copy(message = R.string.save_folder_unavailable, saveAsName = defaultName()) }
            return
        }
        write(target)
    }

    fun saveAs() {
        if (_state.value.canSave) _state.update { it.copy(saveAsName = defaultName()) }
    }

    /** Result of the "save as" dialog */
    fun saveAsChosen(uri: Uri?) {
        if (uri != null) write(uri)
    }

    private fun baseName() = "collage-${FileNaming.today()}"

    private fun defaultName() = "${baseName()}.${directSave.format.extension}"

    /** The collage drawn as it will be saved, smaller, for the preview */
    private fun renderPreview() {
        val state = _state.value
        val layout = state.layout ?: return
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            val preview = withContext(Dispatchers.Default) {
                CollageRenderer.render(app, state.images, layout, state.mode, PREVIEW_SIZE, PREVIEW_SIZE)
            }
            _state.update { if (it.layout == layout) it.copy(preview = preview) else it }
        }
    }

    /**
     * Writes the full size collage to [target]. Runs to completion even if the screen closes,
     * and removes the file if anything fails, so no half-written file is left behind.
     */
    private fun write(target: Uri) {
        val state = _state.value
        val layout = state.layout ?: return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val written = withContext(NonCancellable + Dispatchers.Default) {
                try {
                    val bitmap = CollageRenderer.render(app, state.images, layout, state.mode)
                    val output = app.contentResolver.openOutputStream(target) ?: throw IOException("Cannot open $target")
                    output.writeBitmap(bitmap, directSave.format, directSave.quality)
                    bitmap.recycle()
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Saving the collage failed", e)
                    false
                }
            }
            if (written) {
                // Makes a directly saved file visible; does nothing for "save as" files
                directSave.finish(target)
                _state.update { it.copy(saving = false, result = target) }
            } else {
                directSave.discard()
                _state.update { it.copy(saving = false, message = R.string.save_image_failed) }
            }
        }
    }
}

package org.pixeldroid.media_editor.photoEdit

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.pixeldroid.media_editor.common.PICTURE_POSITION
import org.pixeldroid.media_editor.common.PICTURE_URI
import org.pixeldroid.media_editor.common.ui.BunnyTheme
import org.pixeldroid.media_editor.photoEdit.LogViewActivity.Companion.launchLogView
import org.pixeldroid.media_editor.photoEdit.PhotoEditViewModel.Change
import org.pixeldroid.media_editor.photoEdit.imagine.UriImageProvider
import org.pixeldroid.media_editor.photoEdit.imagine.core.ImagineEngine
import org.pixeldroid.media_editor.photoEdit.imagine.core.ImagineView
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.AdjustmentsLayer
import org.pixeldroid.media_editor.photoEdit.overlay.OverlayRenderer
import java.io.File
import java.io.IOException


class PhotoEditActivity : AppCompatActivity() {

    private lateinit var model: PhotoEditViewModel

    /** Saving is in progress (shows the progress bar) */
    private var saving: Boolean by mutableStateOf(false)

    private var picturePosition: Int? = null

    companion object {
        const val SAVE_TO_NEW_FILE = "save_to_new_file"

        /**
         * Optional String extra, used together with [SAVE_TO_NEW_FILE]: name of a folder inside
         * the device's Pictures folder. If set, edited images are saved there directly, without
         * asking for a location, as "<original name>-1.<ext>" (or -2, -3, ... if that exists).
         * Only on Android 10+; older versions fall back to asking for a location.
         */
        const val SAVE_TO_FOLDER = "save_to_folder"

        /**
         * Optional String extra, used together with [SAVE_TO_NEW_FILE]: a folder chosen with
         * ACTION_OPEN_DOCUMENT_TREE (as Uri string) that the app holds a persistable write
         * permission for. Takes precedence over [SAVE_TO_FOLDER]. Works on all Android versions.
         */
        const val SAVE_TO_FOLDER_URI = "save_to_folder_uri"

        /** Optional String extra: "png" (default) or "jpeg"; used for all saved images */
        const val SAVE_FORMAT = "save_format"

        /** Optional Int extra: JPEG quality 1-100, default [SaveFormat.DEFAULT_JPEG_QUALITY] */
        const val SAVE_JPEG_QUALITY = "save_jpeg_quality"

        private var saveFuture: Job? = null

        private var saveToNewFile: Boolean = false
    }

    /** GL preview of the image with the sliders and filters */
    private lateinit var imagePreview: ImagineView

    /** Overlay (drawings, texts, stickers) and its touch input, placed over the shown image */
    private lateinit var drawingView: DrawingView

    private val snackbarHostState = SnackbarHostState()

    private val cropper = UCropLauncher(this) { cropped ->
        model.doChange(Change.CropChange(cropped))
        loadImage()
    }

    /** All sliders in one pass, reading the original image (see [AdjustmentsLayer]) */
    private val adjustmentsLayer = AdjustmentsLayer()

    /**
     * Layers that always come first in the engine's layer list; the applied filters and the
     * filter being tried are appended after them.
     */
    private val adjustmentLayers: List<ImagineLayer> = listOf(adjustmentsLayer)

    /** Applies the slider layers plus the currently selected filter (if any) to the preview */
    /** Sliders first, then the applied filters in order, then the filter being tried */
    private fun applyLayers() {
        imagineEngine.layers = adjustmentLayers + model.appliedFilters.value + listOfNotNull(model.filter.value)
        imagineEngine.updatePreview()
    }

    /**
     * Before/after comparison: while [showOriginal] is true the preview shows the image
     * without any slider adjustments, filter, drawing, text or stickers (EditorScreen hides
     * the overlay).
     */
    private fun showOriginal(showOriginal: Boolean) {
        if (showOriginal) {
            imagineEngine.layers = emptyList()
            imagineEngine.updatePreview()
        } else {
            applyLayers()
        }
    }

    private lateinit var imagineEngine: ImagineEngine

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        // Callback is invoked after the user selects a media item or closes the photo picker.
        // Use url in callback to give it in Intent to edit activity
        val position = model.stickerChosen.value
        if (uri != null && position != null) model.addSticker(uri, position.first, position.second, stickerAspect(uri))
        model.resetSticker()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Before the screen is built: "Save as" depends on it
        directSave = DirectSave.fromIntent(this, intent)

        LogViewActivity.initLogFile(cacheDir)

        val _model: PhotoEditViewModel by viewModels {
            PhotoEditViewModelFactory()
        }
        model = _model

        imagePreview = ImagineView(this)
        drawingView = DrawingView(this, null).apply { setModel(model) }

        imagineEngine = ImagineEngine(imagePreview)
        imagineEngine.onImageError = {
            Toast.makeText(this, R.string.image_load_failed, Toast.LENGTH_LONG).show()
            finish()
        }
        imagineEngine.layers = adjustmentLayers

        imagineEngine.updatePreview()

        // Handle back pressed button
        onBackPressedDispatcher.addCallback(this) {
            if(model.shownView.value != PhotoEditViewModel.ShownView.Main) {
                model.showMain()
            } else if (noEdits()) {
                this.isEnabled = false
                super.onBackPressedDispatcher.onBackPressed()
            } else {
                val builder = AlertDialog.Builder(this@PhotoEditActivity)
                builder.apply {
                    setMessage(R.string.save_before_returning)
                    setPositiveButton(android.R.string.ok) { _, _ ->
                        saveImageToGallery()
                    }
                    setNegativeButton(R.string.no_cancel_edit) { _, _ ->
                        this@addCallback.isEnabled = false
                        super.onBackPressedDispatcher.onBackPressed()
                    }
                }
                // Create the AlertDialog
                builder.show()
            }
        }

        val initialUri: Uri? = intent.getParcelableExtra(PICTURE_URI)
        picturePosition = intent.getIntExtra(PICTURE_POSITION, 0)
        saveToNewFile = intent.getBooleanExtra(SAVE_TO_NEW_FILE, false)

        model.initialUri = initialUri

        loadImage()

        setContent {
            BunnyTheme {
                val dimensions by imagineEngine.bitmapDimensions.collectAsStateWithLifecycle()
                EditorScreen(
                    model = model,
                    imagePreview = imagePreview,
                    drawingView = drawingView,
                    bitmapDimensions = dimensions,
                    saving = saving,
                    // With a configured save folder, "Save" saves there directly; offer "Save as" next to it
                    showSaveAs = saveToNewFile && directSave.isAvailable,
                    snackbarHostState = snackbarHostState,
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onSave = { saveImageToGallery() },
                    onSaveAs = { saveImageToGallery(askForLocation = true) },
                    onUndo = ::undo,
                    onRedo = ::redo,
                    onReset = ::resetImage,
                    onCompare = ::showOriginal,
                    onCrop = { model.initialUri?.let(cropper::launch) },
                    onFilterError = { showSnackbar(R.string.filter_error, withLog = true) },
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.sliders.collect { sliders ->
                    adjustmentsLayer.sliders = sliders
                    imagineEngine.updatePreview()
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(model.filter, model.appliedFilters) { _, _ -> }.collect { applyLayers() }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.stickerChosen.collect {
                    // Registers a photo picker activity launcher in single-select mode.
                    if (model.stickerChosen.value != null) {
                        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    }
                }
            }
        }
    }

    /** Shows [message], with a button to the log if [withLog] */
    private fun showSnackbar(@StringRes message: Int, withLog: Boolean = false) {
        lifecycleScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = getString(message),
                actionLabel = if (withLog) getString(R.string.view_log) else null,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) launchLogView(this@PhotoEditActivity).onClick(null)
        }
    }

    /** Height / width of an image, 1 if it can't be read */
    private fun stickerAspect(uri: Uri): Float = try {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        if (options.outWidth > 0 && options.outHeight > 0) options.outHeight.toFloat() / options.outWidth else 1f
    } catch (e: Exception) {
        1f
    }

    private fun loadImage() {
        val updateNeeded = imagineEngine.imageProvider != null

        imagineEngine.imageProvider = model.imageUri?.let { UriImageProvider(this, it) }

        if (updateNeeded) imagineEngine.updatePreview()
    }

    override fun onStop() {
        super.onStop()
        saving = false
    }

    override fun onDestroy() {
        super.onDestroy()
        LogViewActivity.deleteLogFile()
    }

    private fun undo() {
        model.undoChange()
        afterHistoryChange()
    }

    private fun redo() {
        model.redoChange()
        afterHistoryChange()
    }

    /** Shows the state after undo/redo (the tabs follow the ViewModel by themselves) */
    private fun afterHistoryChange() {
        drawingView.invalidate()
        // Reload the image in case a crop was undone or redone
        loadImage()
    }

    private fun resetImage() {
        model.reset()
        drawingView.reset()
        //TODO check if necessary imagineEngine.layers?.forEach { it.resetIntensity() }
        loadImage()
    }

    /** The image is loaded into the engine (for tests, instead of waiting a fixed time) */
    @VisibleForTesting
    internal val isImageLoaded: Boolean
        get() = imagineEngine.bitmapDimensions.value != null

    /** Renders [layers] at full size, for tests of the shaders. The callback runs on the main thread. */
    @VisibleForTesting
    internal fun renderForTest(layers: List<ImagineLayer>, onBitmap: (Bitmap?) -> Unit) {
        imagineEngine.layers = layers
        imagineEngine.onBitmap = onBitmap
        imagineEngine.exportBitmap()
    }

    private fun noEdits(): Boolean =
        model.sliders.value.isNeutral
                // No filter applied or being tried. Read from the ViewModel, not from the engine's
                // layers: those are updated asynchronously and could still be behind
                && model.filter.value == null
                && model.appliedFilters.value.isEmpty()
                // If the image Uri has changed, that's also a change (eg cropping)
                && model.imageUri == model.initialUri
                && model.overlay.value.isEmpty

    private fun doneSavingFile(path: String) {
        if (saving) {
            this.runOnUiThread {
                sendBackImage(path, picturePosition)
                saving = false
            }
        }
    }

    // Save to uri, or to a new cached file if null
    private fun saveToFile(uri: Uri?) {
        if (noEdits()) sendBackImage(model.initialUri.toString(), picturePosition)
        else {
            saving = true

            try {
                // Save modified to given uri or else to cache
                val usedUri: Uri =
                    uri ?: File.createTempFile("temp_edit_img", ".${directSave.format.extension}", cacheDir).toUri()
                imagineEngine.onBitmap = { bitmap ->
                    if (bitmap == null) exportIssue()
                    else saveFuture = lifecycleScope.launch {
                        // Same drawing as the preview, at the full image size
                        val overlay = model.overlay.value
                        val stickers = OverlayRenderer.loadStickers(this@PhotoEditActivity, overlay, bitmap.width)
                        OverlayRenderer.draw(Canvas(bitmap), bitmap.width, bitmap.height, overlay, stickers)

                        contentResolver.openOutputStream(usedUri)
                            ?.writeBitmap(bitmap, directSave.format, directSave.quality)
                        directSave.finish(usedUri)
                        doneSavingFile(usedUri.toString())
                    }
                }
                imagineEngine.exportBitmap()
            } catch (e: IOException) {
                exportIssue()
            }
        }
    }

    private fun exportIssue() {
        directSave.discard()
        this.runOnUiThread {
            showSnackbar(R.string.save_image_failed, withLog = true)
            saving = false
        }
    }

    /** "Save as" dialog for the configured format; [name] is without extension */
    private fun launchSaveAs(name: String) {
        val launcher = if (directSave.format == SaveFormat.JPEG) createJpegContract else createPhotoContract
        launcher.launch("$name.${directSave.format.extension}")
    }

    private val createJpegContract =
        registerForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { newFileUri: Uri? ->
            onSaveAsResult(newFileUri)
        }

    private val createPhotoContract =
        registerForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { newFileUri: Uri? ->
            onSaveAsResult(newFileUri)
        }

    private fun onSaveAsResult(newFileUri: Uri?) {
        if (newFileUri != null) {
            saveToFile(newFileUri)
        } else {
            showSnackbar(R.string.save_image_failed)
            saving = false
        }
    }

    /** Direct save into the configured folder (see [SAVE_TO_FOLDER], [SAVE_TO_FOLDER_URI]) */
    private lateinit var directSave: DirectSave

    private fun getFileName(uri: Uri?): String {
        return (if (uri?.scheme == "content") {
            val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        cursor.moveToFirst()
                        cursor.getString(nameIndex)
                    } else null
                }
            name?.let { File(it).nameWithoutExtension } ?: "image"
        } else uri?.path?.substringAfterLast("/", missingDelimiterValue = "image")) ?: "image"
    }

    /**
     * @param askForLocation Always show the "save as" dialog, even if a save folder is configured
     */
    private fun saveImageToGallery(askForLocation: Boolean = false) {
        if (saving) {
            val builder = AlertDialog.Builder(this)
            builder.apply {
                setMessage(R.string.busy_dialog_text)
                setNegativeButton(R.string.busy_dialog_ok_button) { _, _ -> }
            }
            // Create the AlertDialog
            builder.show()
            return
        }
        if (noEdits()) {
            if (saveToNewFile && directSave.isAvailable && !askForLocation) {
                showSnackbar(R.string.no_changes_not_saved)
            } else if (saveToNewFile) {
                val builder = AlertDialog.Builder(this)
                builder.apply {
                    setMessage(R.string.no_changes_save)
                    setPositiveButton(R.string.yes) { _, _ ->
                        launchSaveAs("${getFileName(model.initialUri)}-copy")
                    }
                    setNegativeButton(R.string.no) { _, _ ->
                        saving = true
                        doneSavingFile(model.imageUri.toString())
                    }
                }
                // Create the AlertDialog
                builder.show()
            } else {
                saving = true
                doneSavingFile(model.imageUri.toString())
            }
        } else {
            if (saveToNewFile && !askForLocation && directSave.isAvailable) {
                val originalName = getFileName(model.initialUri)
                val newFile = directSave.create { used ->
                    FileNaming.numberedName(originalName, used, directSave.format.extension)
                }
                if (newFile == null) {
                    // Folder deleted or permission revoked: let the user choose instead
                    showSnackbar(R.string.save_folder_unavailable)
                    launchSaveAs("$originalName-edited")
                } else {
                    saveToFile(newFile)
                }
            } else if (saveToNewFile) {
                launchSaveAs("${getFileName(model.initialUri)}-edited")
            } else {
                saveToFile(uri = null)
            }
        }
    }
}

package org.pixeldroid.media_editor.photoEdit

import android.app.Activity
import android.graphics.BitmapFactory
import org.pixeldroid.media_editor.photoEdit.panel.HistoryControls
import org.pixeldroid.media_editor.common.ui.BunnyTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import org.pixeldroid.media_editor.photoEdit.overlay.OverlayRenderer
import org.pixeldroid.media_editor.photoEdit.overlay.ImageFrame
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View.GONE
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.appcompat.widget.TooltipCompat
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.pixeldroid.media_editor.common.PICTURE_POSITION
import org.pixeldroid.media_editor.common.PICTURE_URI
import org.pixeldroid.media_editor.photoEdit.LogViewActivity.Companion.launchLogView
import org.pixeldroid.media_editor.photoEdit.PhotoEditViewModel.Change
import org.pixeldroid.media_editor.photoEdit.databinding.ActivityPhotoEditBinding
import org.pixeldroid.media_editor.photoEdit.imagine.UriImageProvider
import org.pixeldroid.media_editor.photoEdit.imagine.core.ImagineEngine
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.BrightnessLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.ContrastLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.SaturationLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.HighlightsLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.ShadowsLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.SharpnessLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.TintLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.VignetteSliderLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.WarmthLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.ClarityLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.ExposureLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.GrainLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.VibranceLayer
import java.io.File
import java.io.IOException


class PhotoEditActivity : AppCompatActivity() {

    private lateinit var model: PhotoEditViewModel

    private var saving: Boolean = false

    private var picturePosition: Int? = null

    companion object {
        /** Position of the "Edit" tab (draw, text, stickers, crop) */
        const val EDIT_TAB = 2

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

    private lateinit var binding: ActivityPhotoEditBinding

    /** The shader layer of each slider */
    private val layerFor: Map<Adjustment, ImagineLayer> = mapOf(
        Adjustment.EXPOSURE to ExposureLayer(),
        Adjustment.BRIGHTNESS to BrightnessLayer(),
        Adjustment.CONTRAST to ContrastLayer(),
        Adjustment.HIGHLIGHTS to HighlightsLayer(),
        Adjustment.SHADOWS to ShadowsLayer(),
        Adjustment.CLARITY to ClarityLayer(),
        Adjustment.SATURATION to SaturationLayer(),
        Adjustment.VIBRANCE to VibranceLayer(),
        Adjustment.WARMTH to WarmthLayer(),
        Adjustment.TINT to TintLayer(),
        Adjustment.SHARPNESS to SharpnessLayer(),
        Adjustment.VIGNETTE to VignetteSliderLayer(),
        Adjustment.GRAIN to GrainLayer(),
    )

    /**
     * Layers controlled by the sliders, in rendering order. They always come first in the
     * engine's layer list, an optionally selected filter is appended after them.
     * The first layer reads the full-size source image directly, so it must not be one that
     * samples neighbouring pixels (sharpness, clarity, grain).
     */
    private val adjustmentLayers: List<ImagineLayer> = listOf(
        Adjustment.BRIGHTNESS, Adjustment.EXPOSURE, Adjustment.CONTRAST,
        Adjustment.HIGHLIGHTS, Adjustment.SHADOWS, Adjustment.CLARITY,
        Adjustment.SATURATION, Adjustment.VIBRANCE, Adjustment.WARMTH, Adjustment.TINT,
        Adjustment.SHARPNESS, Adjustment.VIGNETTE, Adjustment.GRAIN,
    ).map { layerFor.getValue(it) }

    /** Applies the slider layers plus the currently selected filter (if any) to the preview */
    private fun applyLayers() {
        val filter = model.filter.value
        imagineEngine.layers = if (filter != null) adjustmentLayers + filter else adjustmentLayers
        imagineEngine.updatePreview()
    }

    /**
     * Before/after comparison: while [showOriginal] is true the preview shows the image
     * without any slider adjustments, filter, drawing, text or stickers.
     */
    private fun showOriginal(showOriginal: Boolean) {
        if (showOriginal) {
            imagineEngine.layers = emptyList()
            imagineEngine.updatePreview()
            binding.drawingView.visibility = INVISIBLE
        } else {
            applyLayers()
            binding.drawingView.visibility = VISIBLE
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
        // Before anything that may build the toolbar menu, which depends on it
        directSave = DirectSave.fromIntent(this, intent)
        binding = ActivityPhotoEditBinding.inflate(layoutInflater)

        setContentView(binding.root)

        setSupportActionBar(binding.topBar)

        LogViewActivity.initLogFile(cacheDir)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)

        val _model: PhotoEditViewModel by viewModels {
            PhotoEditViewModelFactory()
        }
        model = _model

        binding.drawingView.setModel(model)

        imagineEngine = ImagineEngine(binding.imagePreview)
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
                val builder = AlertDialog.Builder(binding.root.context)
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

        setupViewPager(binding.viewPager)
        // Leaving the Edit tab finishes drawing, text or stickers, so touching the photo
        // doesn't draw while adjusting sliders or filters
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position != EDIT_TAB) model.showMain()
            }
        })

        // Undo, redo and reset on top of the photo
        binding.historyControls.setContent {
            BunnyTheme {
                val canUndo by model.canUndo.collectAsStateWithLifecycle()
                val canRedo by model.canRedo.collectAsStateWithLifecycle()
                HistoryControls(canUndo, canRedo, onUndo = ::undo, onRedo = ::redo, onReset = ::resetImage)
            }
        }

        // Press and hold to compare with the original image
        TooltipCompat.setTooltipText(binding.compareButton, getString(R.string.compare_original))
        binding.compareButton.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> showOriginal(true)
                MotionEvent.ACTION_UP -> {
                    showOriginal(false)
                    view.performClick()
                }
                MotionEvent.ACTION_CANCEL -> showOriginal(false)
            }
            true
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.shownView.collect { uiState ->
                    when(uiState){
                        PhotoEditViewModel.ShownView.Main -> showMain()
                        PhotoEditViewModel.ShownView.Draw -> startDraw()
                        PhotoEditViewModel.ShownView.Text -> startDraw()
                        PhotoEditViewModel.ShownView.Sticker -> startDraw()
                        PhotoEditViewModel.ShownView.Crop -> startCrop()
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.sliders.collect { sliders ->
                    Adjustment.entries.forEach { layerFor.getValue(it).intensity = sliders[it] }
                    imagineEngine.updatePreview()
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.filter.collect { applyLayers() }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Wait for bitmapDimensions to be ready (= not null)
                imagineEngine.bitmapDimensions.collect {
                    initDrawView()
                }
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
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                model.overlay.collectLatest { overlay ->
                    binding.drawingView.invalidate()
                    // Sticker images at the size they are shown; the renderer draws them
                    val width = binding.drawingView.width
                    if (overlay.stickers.isNotEmpty() && width > 0) {
                        binding.drawingView.setStickerImages(OverlayRenderer.loadStickers(this@PhotoEditActivity, overlay, width))
                    }
                }
            }
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

    private fun setupViewPager(viewPager: ViewPager2) {
        val tabs: List<() -> Fragment> = listOf(
            { FilterListFragment() },
            { SliderFragment() },
            { DrawingOnTopFragment() },
        )

        //Disable swiping in viewpager
        viewPager.isUserInputEnabled = false

        //FIXME this will not actually use the new fragments created just above,
        // It will fetch existing fragments!
        // So, on orientation change etc, the old fragments are re-used (and they are not bound correctly)
        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun createFragment(position: Int): Fragment {
                return tabs[position]()
            }

            override fun getItemCount(): Int {
                return tabs.size
            }
        }

        TabLayoutMediator(binding.tabs, viewPager) { tab, position ->
            tab.setText(
                when (position) {
                    0 -> R.string.tab_filters
                    1 -> R.string.sliders
                    else -> R.string.edit
                }
            )
        }.attach()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.edit_menu_undo_redo, menu)
        // With a configured save folder, "Save" saves there directly; offer "Save as" next to it
        if (saveToNewFile && directSave.isAvailable) {
            menu.add(Menu.NONE, R.id.action_save_as, 102, R.string.save_as)
                .setIcon(R.drawable.ic_save_as_24dp)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        return true
    }

    override fun onStop() {
        super.onStop()
        saving = false
    }

    override fun onDestroy() {
        super.onDestroy()
        LogViewActivity.deleteLogFile()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> onBackPressedDispatcher.onBackPressed()
            R.id.action_save -> saveImageToGallery()
            R.id.action_save_as -> saveImageToGallery(askForLocation = true)
        }

        return super.onOptionsItemSelected(item)
    }

    private fun undo() {
        model.undoChange()
        afterHistoryChange()
    }

    private fun redo() {
        model.redoChange()
        afterHistoryChange()
    }

    /** Shows the state after undo/redo (the sliders panel follows the ViewModel by itself) */
    private fun afterHistoryChange() {
        binding.drawingView.invalidate()
        // Reload the image in case a crop was undone or redone
        loadImage()
        (supportFragmentManager.findFragmentByTag("f0") as? FilterListFragment)?.resetSelectedFilter(model.filter.value, true)
    }

    private fun resetImage() {
        model.reset()
        // f0 is the filter tab (the sliders panel follows the ViewModel by itself)
        (supportFragmentManager.findFragmentByTag("f0") as? FilterListFragment)?.resetSelectedFilter()
        binding.drawingView.reset()
        //TODO check if necessary imagineEngine.layers?.forEach { it.resetIntensity() }
        loadImage()
    }

    private val startCropForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                handleCropResult(result.data)
            } else if (result.resultCode == UCrop.RESULT_ERROR) {
                handleCropError(result.data)
            }
        }

    private fun showMain() {
        binding.tabs.visibility = VISIBLE
        binding.viewPager.visibility = VISIBLE
        binding.compareButton.visibility = VISIBLE
        binding.topBar.setTitle(R.string.toolbar_title_edit)

        val params = binding.imagePreview.layoutParams as ConstraintLayout.LayoutParams

        if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            params.bottomToBottom = R.id.bottom_guideline
        } else {
            params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            params.endToEnd = R.id.bottom_guideline
        }

        binding.imagePreview.setLayoutParams(params)

        initDrawView()
    }

    /**
     * A tool (draw, text, sticker) was turned on. The tabs stay visible and the photo keeps its
     * size, so the sliders and filters can be used in between (switching tabs finishes the tool).
     */
    private fun startDraw() {
        binding.topBar.setTitle(
            when (model.shownView.value) {
                PhotoEditViewModel.ShownView.Main, PhotoEditViewModel.ShownView.Crop -> return
                PhotoEditViewModel.ShownView.Draw -> R.string.draw
                PhotoEditViewModel.ShownView.Text -> R.string.add_text
                PhotoEditViewModel.ShownView.Sticker -> R.string.stickers
            }
        )
        initDrawView()
    }

    /**
     * Places the drawing view and the sticker frame exactly over the shown image (the image is
     * scaled to fit, with bars on two sides). The overlay is relative to the image, so nothing
     * has to be rescaled when the size changes.
     */
    private fun initDrawView() {
        val dimensions = imagineEngine.bitmapDimensions.value ?: return
        binding.imagePreview.post {
            val frame = ImageFrame(
                dimensions.width, dimensions.height, binding.imagePreview.width, binding.imagePreview.height
            )
            val params = binding.drawingView.layoutParams as ViewGroup.MarginLayoutParams
            params.setMargins(frame.sideBar, frame.topBar, frame.sideBar, frame.topBar)
            binding.drawingView.layoutParams = params
            // Sticker images for the new size
            binding.drawingView.post {
                lifecycleScope.launch {
                    val width = binding.drawingView.width
                    if (width > 0) binding.drawingView.setStickerImages(
                        OverlayRenderer.loadStickers(this@PhotoEditActivity, model.overlay.value, width)
                    )
                }
            }
        }
    }

    private fun startCrop() {
        val file = File.createTempFile("temp_crop_img", ".png", cacheDir)

        // uCrop draws edge to edge since 2.2.11: the status and navigation bars are transparent,
        // only their icons are dark (light theme) or light (dark theme)
        val lightTheme = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) !=
            Configuration.UI_MODE_NIGHT_YES
        val options: UCrop.Options = UCrop.Options().apply {
            setStatusBarLight(lightTheme)
            setNavigationBarLight(lightTheme)
            setToolbarWidgetColor(this@PhotoEditActivity.getColorFromAttr(R.attr.colorOnSurface))
            setToolbarColor(this@PhotoEditActivity.getColorFromAttr(R.attr.colorSurface))
            setActiveControlsWidgetColor(this@PhotoEditActivity.getColorFromAttr(R.attr.colorPrimary))

            setFreeStyleCropEnabled(true)
        }
        val uCrop: UCrop = UCrop.of(model.initialUri!!, Uri.fromFile(file)).withOptions(options)
        startCropForResult.launch(uCrop.getIntent(this))
    }

    private fun handleCropResult(data: Intent?) {
        val resultCrop: Uri? = UCrop.getOutput(data!!)
        if (resultCrop != null) {
            model.doChange(Change.CropChange(resultCrop))
            loadImage()
        } else {
            Toast.makeText(this, R.string.crop_result_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleCropError(data: Intent?) {
        val resultError = data?.let { UCrop.getError(it) }
        if (resultError != null) {
            Toast.makeText(this, "" + resultError, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.crop_result_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun noEdits(): Boolean =
        //TODO maybe have some tolerance for the floating point equality
        imagineEngine.layers?.all { it.initialIntensity == it.intensity } == true
                // Only the slider layers are present, which means there is no filter applied
                && imagineEngine.layers?.size == adjustmentLayers.size
                // If the image Uri has changed, that's also a change (eg cropping)
                && model.imageUri == model.initialUri
                && model.overlay.value.isEmpty

    private fun doneSavingFile(path: String) {
        if (saving) {
            this.runOnUiThread {
                sendBackImage(path, picturePosition)
                binding.progressBarSaveFile.visibility = GONE
                saving = false
            }
        }
    }

    // Save to uri, or to a new cached file if null
    private fun saveToFile(uri: Uri?) {
        if (noEdits()) sendBackImage(model.initialUri.toString(), picturePosition)
        else {
            saving = true
            binding.progressBarSaveFile.visibility = VISIBLE

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
            Snackbar.make(
                binding.root, R.string.save_image_failed, Snackbar.LENGTH_LONG
            ).setAction(R.string.view_log, launchLogView(this)).show()
            binding.progressBarSaveFile.visibility = GONE
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
            Snackbar.make(
                binding.root, getString(R.string.save_image_failed),
                Snackbar.LENGTH_LONG
            ).show()
            binding.progressBarSaveFile.visibility = GONE
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
                Snackbar.make(binding.root, R.string.no_changes_not_saved, Snackbar.LENGTH_LONG).show()
            } else if (saveToNewFile) {
                val builder = AlertDialog.Builder(this)
                builder.apply {
                    setMessage(R.string.no_changes_save)
                    setPositiveButton(R.string.yes) { _, _ ->
                        launchSaveAs("${getFileName(model.initialUri)}-copy")
                    }
                    setNegativeButton(R.string.no) { _, _ ->
                        saving = true
                        binding.progressBarSaveFile.visibility = VISIBLE
                        doneSavingFile(model.imageUri.toString())
                    }
                }
                // Create the AlertDialog
                builder.show()
            } else {
                saving = true
                binding.progressBarSaveFile.visibility = VISIBLE
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
                    Snackbar.make(binding.root, R.string.save_folder_unavailable, Snackbar.LENGTH_LONG).show()
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

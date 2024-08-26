package org.pixeldroid.media_editor.photoEdit

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Menu
import android.view.MenuItem
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.ActivityResult
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
import kotlinx.coroutines.launch
import org.pixeldroid.media_editor.common.PICTURE_POSITION
import org.pixeldroid.media_editor.common.PICTURE_URI
import org.pixeldroid.media_editor.photoEdit.LogViewActivity.Companion.launchLogView
import org.pixeldroid.media_editor.photoEdit.databinding.ActivityPhotoEditBinding
import org.pixeldroid.media_editor.photoEdit.imagine.UriImageProvider
import org.pixeldroid.media_editor.photoEdit.imagine.core.ImagineEngine
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.BrightnessLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.ContrastLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.SaturationLayer
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors.newSingleThreadExecutor
import java.util.concurrent.Future
import kotlin.math.min
import kotlin.math.roundToInt


class PhotoEditActivity : AppCompatActivity() {

    private var lastDrawingHeight: Int = -1
    private var lastDrawingWidth: Int = -1

    private lateinit var model: PhotoEditViewModel

    private var saving: Boolean = false

    private var picturePosition: Int? = null

    companion object {
        const val SAVE_TO_NEW_FILE = "save_to_new_file"

        private var saveExecutor: ExecutorService = newSingleThreadExecutor()
        private var saveFuture: Future<*>? = null

        private var initialUri: Uri? = null
        private var saveToNewFile: Boolean = false
        internal var imageUri: Uri? = null
    }

    private lateinit var binding: ActivityPhotoEditBinding

    private val brightnessLayer = BrightnessLayer()
    private val contrastLayer = ContrastLayer()
    private val saturationLayer = SaturationLayer()

    private lateinit var imagineEngine: ImagineEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        imagineEngine = ImagineEngine(binding.imagePreview)
        imagineEngine.layers = listOf(
            brightnessLayer, contrastLayer, saturationLayer
        )

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

        initialUri = intent.getParcelableExtra(PICTURE_URI)
        picturePosition = intent.getIntExtra(PICTURE_POSITION, 0)
        saveToNewFile = intent.getBooleanExtra(SAVE_TO_NEW_FILE, false)

        imageUri = initialUri

        // Crop button on-click listener
        binding.cropImageButton.setOnClickListener {
            startCrop()
        }

        loadImage()

        setupViewPager(binding.viewPager)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.shownView.collect { uiState ->
                    when(uiState){
                        PhotoEditViewModel.ShownView.Main -> showMain()
                        PhotoEditViewModel.ShownView.Draw -> startDraw()
                        PhotoEditViewModel.ShownView.Text -> TODO()
                        PhotoEditViewModel.ShownView.Sticker -> TODO()
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.sliders.collect { sliders ->
                    brightnessLayer.intensity = sliders.brightness
                    contrastLayer.intensity = sliders.contrast
                    saturationLayer.intensity = sliders.saturation
                    imagineEngine.updatePreview()
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.filter.collect { filter ->
                    if (filter != null) {
                        imagineEngine.layers = imagineEngine.layers?.subList(0, 3)?.plus(filter)
                    } else imagineEngine.layers = imagineEngine.layers?.subList(0, 3)

                    imagineEngine.updatePreview()
                }
            }
        }

    }

    private fun loadImage() {
        val updateNeeded = imagineEngine.imageProvider != null

        imagineEngine.imageProvider = imageUri?.let { UriImageProvider(this, it) }

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
        menuInflater.inflate(R.menu.edit_menu, menu)
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
            R.id.action_save -> {
                saveImageToGallery()
            }

            R.id.action_reset -> {
                resetImage()
            }
        }

        return super.onOptionsItemSelected(item)
    }

    private fun resetImage() {
        model.reset()
        binding.drawingView.reset()
        //TODO check if necessary imagineEngine.layers?.forEach { it.resetIntensity() }
        imageUri = initialUri
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
        binding.cropImageButton.visibility = VISIBLE
        binding.topBar.setTitle(R.string.toolbar_title_edit)

        val params = binding.imagePreview.layoutParams as ConstraintLayout.LayoutParams
        params.bottomToBottom = R.id.bottom_guideline
        binding.imagePreview.setLayoutParams(params)

        initDrawView()
        binding.drawingView.drawEnabled = true
    }

    private fun startDraw() {
        binding.tabs.visibility = GONE
        binding.viewPager.visibility = GONE
        binding.cropImageButton.visibility = GONE
        binding.topBar.setTitle(R.string.draw)

        val params = binding.imagePreview.layoutParams as ConstraintLayout.LayoutParams
        params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        binding.imagePreview.setLayoutParams(params)

        initDrawView()
        binding.drawingView.drawEnabled = true
    }

    private fun initDrawView() {
        val bitmapDimensions = imagineEngine.bitmapDimensions ?: return
        val bitmapRatio = bitmapDimensions.let {
            it.width.toDouble() / it.height.toDouble()
        }

        // Need the dimensions so post on the view
        binding.imagePreview.post {
            val previousDrawingWidth = lastDrawingWidth
            val previousDrawingHeight = lastDrawingHeight

            lastDrawingWidth = binding.imagePreview.width
            lastDrawingHeight = binding.imagePreview.height
            val viewRatio =
                lastDrawingWidth.toDouble() / lastDrawingHeight.toDouble()

            val blackBarWidth: Int
            val blackBarHeight: Int

            if (bitmapRatio > viewRatio) {
                // Scale by width, black bars on top and bottom
                val scaledHeight = (lastDrawingWidth.toDouble() / bitmapRatio).roundToInt()
                blackBarWidth = 0
                blackBarHeight = (lastDrawingHeight - scaledHeight) / 2
            } else {
                // Scale by height, black bars on sides
                val scaledWidth = (lastDrawingHeight.toDouble() * bitmapRatio).roundToInt()
                blackBarWidth = (lastDrawingWidth - scaledWidth) / 2
                blackBarHeight = 0
            }
            val layoutParams = binding.drawingView.layoutParams as ViewGroup.MarginLayoutParams
            layoutParams.setMargins(blackBarWidth, blackBarHeight, blackBarWidth, blackBarHeight)
            binding.drawingView.layoutParams = layoutParams

            // Calculate scale factors
            val scaleX: Float =
                lastDrawingWidth.toFloat() / previousDrawingWidth
            val scaleY: Float =
                lastDrawingHeight.toFloat() / previousDrawingHeight

            // Scale the Path
            val originalPath: Path = binding.drawingView.path
            val scaleMatrix = Matrix().apply { setScale(scaleX, scaleY) }
            val scaledPath = Path()
            originalPath.transform(scaleMatrix, scaledPath)

            // Scale the Paint's Stroke Width
            val scaledPaint =
                Paint(binding.drawingView.paint) // Create a copy of the original paint
            scaledPaint.strokeWidth =
                (binding.drawingView.paint.strokeWidth * min(
                    scaleX.toDouble(),
                    scaleY.toDouble()
                )).toFloat()

            binding.drawingView.setDrawing(scaledPaint, scaledPath)
        }
    }

    private fun startCrop() {
        val file = File.createTempFile("temp_crop_img", ".png", cacheDir)

        val options: UCrop.Options = UCrop.Options().apply {
            setStatusBarColor(this@PhotoEditActivity.getColorFromAttr(R.attr.colorPrimaryDark))
            setToolbarWidgetColor(this@PhotoEditActivity.getColorFromAttr(R.attr.colorOnSurface))
            setToolbarColor(this@PhotoEditActivity.getColorFromAttr(R.attr.colorSurface))
            setActiveControlsWidgetColor(this@PhotoEditActivity.getColorFromAttr(R.attr.colorPrimary))

            setFreeStyleCropEnabled(true)
        }
        val uCrop: UCrop = UCrop.of(initialUri!!, Uri.fromFile(file)).withOptions(options)
        startCropForResult.launch(uCrop.getIntent(this))
    }

    private fun handleCropResult(data: Intent?) {
        val resultCrop: Uri? = UCrop.getOutput(data!!)
        if (resultCrop != null) {
            imageUri = resultCrop
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

    private fun sendBackImage(file: String) {
        val intent = Intent()
            .apply {
                putExtra(PICTURE_URI, file)
                putExtra(PICTURE_POSITION, picturePosition)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }

        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    private fun OutputStream.writeBitmap(bitmap: Bitmap) {
        use { out ->
            //(quality is ignored for PNG)
            bitmap.compress(Bitmap.CompressFormat.PNG, 85, out)
            out.flush()
        }
    }

    private fun noEdits(): Boolean =
        //TODO maybe have some tolerance for the floating point equality
        imagineEngine.layers?.all { it.initialIntensity == it.intensity } == true
                // There are only 3 layers (brightness, contrast, saturation), which means
                // there is no filter applied
                && imagineEngine.layers?.size == 3
                // If the image Uri has changed, that's also a change (eg cropping)
                && imageUri == initialUri

    private fun doneSavingFile(path: String) {
        if (saving) {
            this.runOnUiThread {
                sendBackImage(path)
                binding.progressBarSaveFile.visibility = GONE
                saving = false
            }
        }
    }

    // Save to uri, or to a new cached file if null
    private fun saveToFile(uri: Uri?) {
        if (noEdits()) sendBackImage(initialUri.toString())
        else {
            saving = true
            binding.progressBarSaveFile.visibility = VISIBLE

            try {
                // Save modified to given uri or else to cache
                val usedUri: Uri =
                    uri ?: File.createTempFile("temp_edit_img", ".png", cacheDir).toUri()
                imagineEngine.onBitmap = { bitmap ->
                    if (bitmap == null) exportIssue()
                    else saveFuture = saveExecutor.submit {
                        // Calculate scale factors
                        val scaleX: Float =
                            bitmap.getWidth().toFloat() / lastDrawingWidth
                        val scaleY: Float =
                            bitmap.getHeight().toFloat() / lastDrawingHeight

                        // Scale the Path
                        val originalPath: Path = binding.drawingView.path
                        val scaleMatrix = Matrix().apply { setScale(scaleX, scaleY) }
                        val scaledPath = Path()
                        originalPath.transform(scaleMatrix, scaledPath)

                        // Scale the Paint's Stroke Width
                        val scaledPaint =
                            Paint(binding.drawingView.paint) // Create a copy of the original paint
                        scaledPaint.strokeWidth =
                            (binding.drawingView.paint.strokeWidth * min(
                                scaleX.toDouble(),
                                scaleY.toDouble()
                            )).toFloat()

                        Canvas(bitmap).drawPath(scaledPath, scaledPaint)
                        contentResolver.openOutputStream(usedUri)?.writeBitmap(bitmap)
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
        this.runOnUiThread {
            Snackbar.make(
                binding.root, R.string.save_image_failed, Snackbar.LENGTH_LONG
            ).setAction(R.string.view_log, launchLogView(this)).show()
            binding.progressBarSaveFile.visibility = GONE
            saving = false
        }
    }

    private val createPhotoContract =
        registerForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { newFileUri: Uri? ->
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

    private fun saveImageToGallery() {
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
            if (saveToNewFile) {
                val builder = AlertDialog.Builder(this)
                builder.apply {
                    setMessage(R.string.no_changes_save)
                    setPositiveButton(R.string.yes) { _, _ ->
                        createPhotoContract.launch("${getFileName(initialUri)}-copy.png")
                    }
                    setNegativeButton(R.string.no) { _, _ ->
                        saving = true
                        binding.progressBarSaveFile.visibility = VISIBLE
                        doneSavingFile(imageUri.toString())
                    }
                }
                // Create the AlertDialog
                builder.show()
            } else {
                saving = true
                binding.progressBarSaveFile.visibility = VISIBLE
                doneSavingFile(imageUri.toString())
            }
        } else {
            if (saveToNewFile) {
                createPhotoContract.launch("${getFileName(initialUri)}-edited.png")
            } else {
                saveToFile(uri = null)
            }
        }
    }
}

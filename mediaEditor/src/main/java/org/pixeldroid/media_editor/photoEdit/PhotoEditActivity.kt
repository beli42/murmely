package org.pixeldroid.media_editor.photoEdit

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Menu
import android.view.MenuItem
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import com.yalantis.ucrop.UCrop
import com.zomato.photofilters.imageprocessors.Filter
import org.pixeldroid.media_editor.R
import org.pixeldroid.media_editor.databinding.ActivityPhotoEditBinding
import org.pixeldroid.media_editor.photoEdit.imagine.UriImageProvider
import org.pixeldroid.media_editor.photoEdit.imagine.core.ImagineEngine
import org.pixeldroid.media_editor.photoEdit.imagine.layers.BrightnessLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.ContrastLayer
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors.newSingleThreadExecutor
import java.util.concurrent.Future


class PhotoEditActivity : AppCompatActivity() {

    private var saving: Boolean = false

    private lateinit var filterListFragment: FilterListFragment
    private lateinit var editImageFragment: EditImageFragment

    private var picturePosition: Int? = null

    init {
        System.loadLibrary("NativeImageProcessor")
    }

    companion object{
        const val PICTURE_URI = "picture_uri"
        const val PICTURE_POSITION = "picture_position"
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
    private val saturationLayer = BrightnessLayer()

    private lateinit var imagineEngine: ImagineEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoEditBinding.inflate(layoutInflater)

        setContentView(binding.root)

        setSupportActionBar(binding.topBar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)


        imagineEngine = ImagineEngine(binding.imagePreview)
        imagineEngine.layers = listOf(
            brightnessLayer, contrastLayer, saturationLayer
        )

        imagineEngine.updatePreview()

            // Handle back pressed button
        onBackPressedDispatcher.addCallback(this) {
            if (noEdits()) {
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
    }

    private fun loadImage() {
        val updateNeeded = imagineEngine.imageProvider != null

        imagineEngine.imageProvider = imageUri?.let { UriImageProvider(this, it) }

        if (updateNeeded) imagineEngine.updatePreview()
    }

    private fun setupViewPager(viewPager: ViewPager2) {
        filterListFragment = FilterListFragment()
        filterListFragment.setListener(::onFilterSelected)

        editImageFragment = EditImageFragment()
        editImageFragment.setListener(this)

        val tabs: List<() -> Fragment> = listOf({ filterListFragment }, { editImageFragment })

        // Keep both tabs loaded at all times because values are needed there
        viewPager.offscreenPageLimit = 1

        //Disable swiping in viewpager
        viewPager.isUserInputEnabled = false

        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun createFragment(position: Int): Fragment {
                return tabs[position]()
            }

            override fun getItemCount(): Int {
                return tabs.size
            }
        }
        TabLayoutMediator(binding.tabs, viewPager) { tab, position ->
            tab.setText(when(position) {
                0 -> R.string.tab_filters
                else -> R.string.edit
            })
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when(item.itemId) {
            android.R.id.home -> onBackPressedDispatcher.onBackPressed()
            R.id.action_save -> {
                saveImageToGallery()
            }
            R.id.action_reset -> {
                resetControls()
                resetImage()
            }
        }

        return super.onOptionsItemSelected(item)
    }

    private fun resetImage() {
        filterListFragment.resetSelectedFilter()
        imagineEngine.layers?.forEach { it.resetIntensity() }
        imageUri = initialUri
        loadImage()
    }

    fun onFilterSelected(filter: Filter) {
        /* TODO
        filteredImage = compressedOriginalImage!!.copy(BITMAP_CONFIG, true)
        binding.imagePreview.setImageBitmap(filter.processFilter(filteredImage))
        compressedImage = filteredImage.copy(BITMAP_CONFIG, true)
        actualFilter = filter
        resetControls()
         */
    }

    private fun resetControls() {
        editImageFragment.resetControl()
    }

    fun onBrightnessChange(brightness: Float) {
        brightnessLayer.intensity = brightness
        imagineEngine.updatePreview()
    }

    fun onSaturationChange(saturation: Float) {
        saturationLayer.intensity = saturation
        imagineEngine.updatePreview()
    }

    fun onContrastChange(contrast: Float) {
        contrastLayer.intensity = contrast
        imagineEngine.updatePreview()
    }

    fun onEditStarted() {
    }

    fun onEditCompleted() {
        //TODO filters
    }

    private val startCropForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                handleCropResult(result.data)
            } else {
                handleCropError(result.data)
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
        if(resultCrop != null) {
            imageUri = resultCrop
            loadImage()
        } else {
            Toast.makeText(this, R.string.crop_result_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleCropError(data: Intent?) {
        val resultError = data?.let { UCrop.getError(it) }
        if(resultError != null) {
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
        imagineEngine.layers?.all{it.initialIntensity == it.intensity} == true
                // There are only 3 layers (brightness, contrast, saturation), which means
                // there is no filter applied
                && imagineEngine.layers?.size == 3
                    // If the image Uri has changed, that's also a change (eg cropping)
                    && imageUri == initialUri

    private fun doneSavingFile(path: String) {
        if(saving) {
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
                    saveFuture = saveExecutor.submit {
                        contentResolver.openOutputStream(usedUri)?.writeBitmap(bitmap)
                        doneSavingFile(usedUri.toString())
                    }
                }
                imagineEngine.exportBitmap()
            } catch (e: IOException) {
                this.runOnUiThread {
                    Snackbar.make(
                        binding.root, getString(R.string.save_image_failed),
                        Snackbar.LENGTH_LONG
                    ).show()
                    binding.progressBarSaveFile.visibility = GONE
                    saving = false
                }
            }
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
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if(nameIndex >= 0) {
                        cursor.moveToFirst()
                        cursor.getString(nameIndex)
                    } else null
                }
        } else uri?.path?.substringAfterLast("/", missingDelimiterValue = "image") ) ?: "image"
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
            if(saveToNewFile){
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
                createPhotoContract.launch("edited.png")
            } else {
                saveToFile(uri = null)
            }
        }
    }
}

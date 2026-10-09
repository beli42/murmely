package org.pixeldroid.media_editor.photoEdit.ui.main.ui.main

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.Paint
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.View.GONE
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pixeldroid.media_editor.photoEdit.DirectSave
import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.SaveFormat
import org.pixeldroid.media_editor.photoEdit.databinding.FragmentCollageChooseImagesBinding
import org.pixeldroid.media_editor.photoEdit.getBitmap
import org.pixeldroid.media_editor.photoEdit.sendBackImage
import org.pixeldroid.media_editor.photoEdit.ui.main.ui.main.CollageFragment.Companion.ACTION_IDENTIFIER
import org.pixeldroid.media_editor.photoEdit.writeBitmap
import java.util.SortedMap

class ChooseImagesFragment : Fragment() {
    private var whichCollage: Int? = null
    private val viewModel: MainViewModel by activityViewModels()

    private lateinit var binding: FragmentCollageChooseImagesBinding

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCollageChooseImagesBinding.inflate(inflater, container, false)
        whichCollage = arguments?.getInt(ACTION_IDENTIFIER)

        when (whichCollage) {
            1 -> {
                binding.image.setOnTouchListener { v, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        if (event.x / v.width > 0.4 || event.y / v.height > 0.4) {
                            viewModel.clickedImage(1)
                        } else {
                            viewModel.clickedImage(2)
                        }
                    }
                    true
                }
                binding.image.setImageResource(R.drawable.bitmap)
            }
            2 -> {
                binding.image.setOnTouchListener { v, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        if (event.x / v.width < 0.5) {
                            viewModel.clickedImage(1)
                        } else {
                            viewModel.clickedImage(2)
                        }
                    }
                    true
                }
                binding.image.setImageResource(R.drawable.split_middle_vertical_collage)
            }
            3 -> {
                binding.image.setOnTouchListener { v, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        if (event.y / v.height < 0.5) {
                            viewModel.clickedImage(1)
                        } else {
                            viewModel.clickedImage(2)
                        }
                    }
                    true
                }
                binding.image.setImageResource(R.drawable.split_middle_horizontal_collage)
            }
            4 -> {
                binding.image.setOnTouchListener { v, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        if (event.y / v.height < 0.5 && event.x / v.width < 0.5) {
                            viewModel.clickedImage(1)
                        } else if (event.y / v.height < 0.5 && event.x / v.width > 0.5){
                            viewModel.clickedImage(2)
                        } else if (event.y / v.height > 0.5 && event.x / v.width < 0.5){
                            viewModel.clickedImage(3)
                        } else if (event.y / v.height > 0.5 && event.x / v.width > 0.5){
                            viewModel.clickedImage(4)
                        }
                    }
                    true
                }
                binding.image.setImageResource(R.drawable.split_4_collage)
            }
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                combine(viewModel.imageUris, viewModel.fitWholeImage) { images, fit -> images to fit }
                    .collect { (images, fit) ->
                    binding.collageFrameLayout.removeAllViews()
                    images.forEach { (whichImage, uri) ->
                        val collageImageView = ImageView(binding.root.context)

                        var scaledStickerWidth: Int
                        var scaledStickerHeight: Int
                        when (whichCollage) {
                            1 -> {
                                binding.image.post {
                                    scaledStickerWidth = if (whichImage == 1) binding.image.width else (binding.image.width * 0.4).toInt()
                                    scaledStickerHeight = if (whichImage == 1) binding.image.height else (binding.image.height * 0.4).toInt()

                                    val layoutParams =
                                        FrameLayout.LayoutParams(
                                            scaledStickerWidth,
                                            scaledStickerHeight
                                        )
                                    collageImageView.setLayoutParams(layoutParams)
                                    collageImageView.x = binding.image.x
                                    collageImageView.y = binding.image.y
                                }
                            }

                            2 -> {
                                binding.image.post {
                                    scaledStickerWidth = (binding.image.width * 0.5).toInt()
                                    scaledStickerHeight = binding.image.height

                                    val layoutParams =
                                        FrameLayout.LayoutParams(
                                            scaledStickerWidth,
                                            scaledStickerHeight
                                        )
                                    collageImageView.setLayoutParams(layoutParams)
                                    collageImageView.x = binding.image.x + if(whichImage == 1) 0f else binding.image.width.toFloat() / 2
                                    collageImageView.y = binding.image.y
                                }
                            }
                            3 -> {
                                binding.image.post {
                                    scaledStickerWidth = binding.image.width
                                    scaledStickerHeight = (binding.image.height * 0.5).toInt()

                                    val layoutParams =
                                        FrameLayout.LayoutParams(
                                            scaledStickerWidth,
                                            scaledStickerHeight.toInt()
                                        )
                                    collageImageView.setLayoutParams(layoutParams)
                                    collageImageView.x = binding.image.x
                                    collageImageView.y = binding.image.y + if(whichImage == 1) 0f else binding.image.height.toFloat() / 2
                                }
                            }

                            4 -> {
                                binding.image.post {
                                    scaledStickerWidth = (binding.image.width * 0.5).toInt()
                                    scaledStickerHeight = (binding.image.height * 0.5).toInt()

                                    val layoutParams =
                                        FrameLayout.LayoutParams(
                                            scaledStickerWidth,
                                            scaledStickerHeight
                                        )
                                    collageImageView.setLayoutParams(layoutParams)
                                    collageImageView.x = binding.image.x + if(whichImage % 2 == 1) 0f else binding.image.width.toFloat() / 2
                                    collageImageView.y = binding.image.y + if(whichImage <= 2) 0f else binding.image.height.toFloat() / 2

                                }
                            }
                        }

                        // Same scaling as when saving (see writeCollage), set explicitly
                        collageImageView.adjustViewBounds = false
                        if (fit) {
                            collageImageView.scaleType = ImageView.ScaleType.FIT_CENTER
                            collageImageView.setBackgroundColor(Color.WHITE)
                        } else {
                            collageImageView.scaleType = ImageView.ScaleType.CENTER_CROP
                        }

                        binding.collageFrameLayout.addView(collageImageView)
                        Glide.with(this@ChooseImagesFragment).load(uri)
                            .let { if (fit) it.fitCenter() else it.centerCrop() }
                            .into(collageImageView)
                        binding.collageFrameLayout.requestLayout()

                    }
                }
            }
        }

        val menuHost: MenuHost = requireActivity()

        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_collage, menu)
                // Toggle between showing whole images and cropping them to fill their area.
                // The icon shows what a tap will do.
                val fit = viewModel.fitWholeImage.value
                menu.add(Menu.NONE, R.id.action_collage_fit, 90,
                    if (fit) R.string.collage_mode_crop_action else R.string.collage_mode_fit_action)
                    .setIcon(if (fit) R.drawable.ic_crop_24dp else R.drawable.ic_fit_screen_24dp)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                // With a configured save folder, "Save" saves there directly; offer "Save as" too
                if (directSave.isAvailable) {
                    menu.add(Menu.NONE, R.id.action_save_as, 102, R.string.save_as)
                        .setIcon(R.drawable.ic_save_as_24dp)
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                }
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_save_collage -> {
                        // Ignore further taps while saving (a double tap created two files)
                        if (saving) return true
                        if (directSave.isAvailable) saveDirectly()
                        else launchSaveAs()
                        true
                    }
                    R.id.action_collage_fit -> {
                        viewModel.toggleFitWholeImage()
                        requireActivity().invalidateMenu()
                        Snackbar.make(
                            binding.root,
                            if (viewModel.fitWholeImage.value) R.string.collage_mode_fit
                            else R.string.collage_mode_crop,
                            Snackbar.LENGTH_SHORT
                        ).show()
                        true
                    }
                    R.id.action_save_as -> {
                        if (saving) return true
                        launchSaveAs()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        return binding.root
    }
    /** Direct save into the configured folder, passed on by the app in the activity's Intent */
    private val directSave: DirectSave by lazy {
        DirectSave.fromIntent(requireContext(), requireActivity().intent)
    }

    /** "collage-2026-10-09" */
    private fun baseName() = "collage-${DirectSave.today()}"

    /** "Save as" dialog for the configured format */
    private fun launchSaveAs() {
        val launcher = if (directSave.format == SaveFormat.JPEG) createJpegContract else createPhotoContract
        launcher.launch("${baseName()}.${directSave.format.extension}")
    }

    /** Saves as collage-<date>.<ext> (or -1, -2, ... if that exists) in the configured folder */
    private fun saveDirectly() {
        val base = baseName()
        val target = directSave.create { used ->
            DirectSave.nameWithOptionalNumber(base, used, directSave.format.extension)
        }
        if (target == null) {
            // Folder deleted or permission revoked: let the user choose instead
            Snackbar.make(binding.root, R.string.save_folder_unavailable, Snackbar.LENGTH_LONG).show()
            launchSaveAs()
            return
        }
        startSaving(target)
    }

    /** True while a collage is being written */
    private var saving = false

    /**
     * Writes the collage to [target]. Runs to completion even if the screen is closed in the
     * meantime, so no half-written file is left behind.
     */
    private fun startSaving(target: Uri) {
        saving = true
        val images = viewModel.imageUris.value
        val layout = whichCollage!!
        val fit = viewModel.fitWholeImage.value
        lifecycleScope.launch {
            try {
                withContext(NonCancellable) { saveCollage(target, images, layout, fit) }
            } finally {
                saving = false
            }
        }
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
            startSaving(newFileUri)
        } else {
            Snackbar.make(
                binding.root, getString(R.string.save_image_failed),
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private suspend fun saveCollage(targetUri: Uri, images: SortedMap<Int, Uri>, whichCollage: Int, fit: Boolean) {
        try {
            writeCollage(targetUri, images, whichCollage, fit)
        } catch (e: Exception) {
            // Never leave a broken file behind
            directSave.discard()
            Snackbar.make(binding.root, getString(R.string.save_image_failed), Snackbar.LENGTH_LONG).show()
            return
        }
        // Makes a directly saved file visible; does nothing for "save as" files
        directSave.finish(targetUri)
        requireActivity().sendBackImage(targetUri.toString(), null)
    }

    /** Area of image [whichImage] in a [width] x [height] collage of layout [whichCollage] */
    private fun cellFor(whichCollage: Int, whichImage: Int, width: Int, height: Int): Rect =
        when (whichCollage) {
            // Big image with a small one (40%) in the top left corner
            1 -> if (whichImage == 1) Rect(0, 0, width, height)
                 else Rect(0, 0, (width * 0.4).toInt(), (height * 0.4).toInt())
            // Left | right
            2 -> if (whichImage == 1) Rect(0, 0, width / 2, height) else Rect(width / 2, 0, width, height)
            // Top / bottom
            3 -> if (whichImage == 1) Rect(0, 0, width, height / 2) else Rect(0, height / 2, width, height)
            // 2 x 2 grid
            else -> {
                val left = if (whichImage % 2 == 1) 0 else width / 2
                val top = if (whichImage <= 2) 0 else height / 2
                Rect(left, top, left + width / 2, top + height / 2)
            }
        }

    private suspend fun writeCollage(targetUri: Uri, images: SortedMap<Int, Uri>, whichCollage: Int, fit: Boolean) {
        val width = 2000
        val height = 2000

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val white = Paint().apply { color = Color.WHITE }

        images.forEach { (whichImage, uri) ->
            val cell = cellFor(whichCollage, whichImage, width, height)
            val image = getBitmap(requireContext(), uri, cell.width(), cell.height(), fit)
                ?: return@forEach
            // Whole image: white bars around it (also covers what is behind a small inset image)
            if (fit) canvas.drawRect(cell, white)
            // Centered in its area (when cropped the image is exactly the area's size)
            canvas.drawBitmap(
                image,
                cell.left + (cell.width() - image.width) / 2f,
                cell.top + (cell.height() - image.height) / 2f,
                null
            )
        }

        val out = requireActivity().contentResolver.openOutputStream(targetUri)
            ?: throw java.io.IOException("Cannot open $targetUri")
        out.writeBitmap(bitmap, directSave.format, directSave.quality)
    }
}
package org.pixeldroid.media_editor.photoEdit.ui.main.ui.main

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
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
import kotlinx.coroutines.launch
import org.pixeldroid.media_editor.photoEdit.R
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
                viewModel.imageUris.collect {
                    binding.collageFrameLayout.removeAllViews()
                    it.forEach { (whichImage, uri) ->
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

                       collageImageView.adjustViewBounds = true
                       collageImageView.scaleType = ImageView.ScaleType.CENTER_CROP

                        binding.collageFrameLayout.addView(collageImageView)
                        Glide.with(this@ChooseImagesFragment).load(uri)
                           // .override(scaledStickerWidth, scaledStickerHeight)
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
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_save_collage -> {
                        createPhotoContract.launch("collage.png")
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        return binding.root
    }
    private val createPhotoContract =
        registerForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { newFileUri: Uri? ->
            if (newFileUri != null) {
                lifecycleScope.launch {
                    saveCollage(newFileUri, viewModel.imageUris.value, whichCollage!!)
                }
            } else {
                Snackbar.make(
                    binding.root, getString(R.string.save_image_failed),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }

    private suspend fun saveCollage(targetUri: Uri, images: SortedMap<Int, Uri>, whichCollage: Int) {
        val width = 2000
        val height = 2000

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(bitmap)

        images.forEach { (whichImage, uri) ->
            when (whichCollage) {
                1 -> {
                    val bitmapWidth = if (whichImage == 1) width else width * 0.4
                    val bitmapHeight = if (whichImage == 1) height else height * 0.4

                    val collageBitmap: Bitmap? =
                        getBitmap(requireContext(), uri, bitmapWidth.toInt(), bitmapHeight.toInt())

                    if (collageBitmap != null) {
                        canvas.drawBitmap(collageBitmap, 0f, 0f, null)
                    }
                }

                2 -> {
                    val bitmapWidth = width * 0.5
                    val bitmapHeight = height

                    val collageBitmap: Bitmap? =
                        getBitmap(requireContext(), uri, bitmapWidth.toInt(), bitmapHeight)

                    if (collageBitmap != null) {
                        canvas.drawBitmap(
                            collageBitmap,
                            if(whichImage == 1) 0f else width.toFloat() / 2,
                            0f,
                            null
                        )
                    }
                }
                3 -> {
                    val bitmapWidth = width
                    val bitmapHeight = height * 0.5

                    val collageBitmap: Bitmap? =
                        getBitmap(requireContext(), uri, bitmapWidth, bitmapHeight.toInt())

                    if (collageBitmap != null) {
                        canvas.drawBitmap(collageBitmap,
                            0f,
                            if(whichImage == 1) 0f else height.toFloat() / 2,
                            null)
                    }
                }

                4 -> {
                    val bitmapWidth = width * 0.5
                    val bitmapHeight = height * 0.5

                    val collageBitmap: Bitmap? =
                        getBitmap(requireContext(), uri, bitmapWidth.toInt(), bitmapHeight.toInt())

                    if (collageBitmap != null) {
                        canvas.drawBitmap(collageBitmap,
                            if(whichImage % 2 == 1) 0f else width.toFloat() / 2,
                            if(whichImage <= 2) 0f else height.toFloat() / 2,
                            null)
                    }
                }
            }
        }

        requireActivity().contentResolver.openOutputStream(targetUri)?.writeBitmap(bitmap)
        requireActivity().sendBackImage(targetUri.toString(), null)
    }
}
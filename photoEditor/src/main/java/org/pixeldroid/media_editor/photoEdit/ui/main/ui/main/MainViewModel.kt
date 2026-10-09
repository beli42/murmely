package org.pixeldroid.media_editor.photoEdit.ui.main.ui.main

import android.net.Uri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.SortedMap

class MainViewModel : ViewModel() {
    private val _clickedImage = MutableStateFlow<Int?>(null)
    val clickedImage = _clickedImage.asStateFlow()

    // Sorted because the order of the images is important if one is on top of another
    private val _imageUris = MutableStateFlow<SortedMap<Int, Uri>>(emptyMap<Int, Uri>().toSortedMap())
    val imageUris = _imageUris.asStateFlow()

    /** How each image is fitted into its area of the collage */
    enum class ScaleMode {
        /** Whole image, original aspect ratio, white bars */
        FIT,
        /** Fills the area, original aspect ratio, overflow cropped */
        CROP,
        /** Fills the area, aspect ratio changed to match */
        STRETCH;

        fun next(): ScaleMode = entries[(ordinal + 1) % entries.size]
    }

    private val _scaleMode = MutableStateFlow(ScaleMode.FIT)
    val scaleMode = _scaleMode.asStateFlow()

    /** FIT -> CROP -> STRETCH -> FIT */
    fun cycleScaleMode() { _scaleMode.value = _scaleMode.value.next() }

    fun clickedImage(i: Int) { _clickedImage.value = i }

    private fun resetClickedImage() { _clickedImage.value = null }

    fun gotImage(uri: Uri?) {
        uri?.let {
            _clickedImage.value?.let {
                _imageUris.value += Pair(it, uri)
            }
        }
        resetClickedImage()
    }

}
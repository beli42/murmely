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

    /** true: show each image completely (white bars), false: crop to fill its area */
    private val _fitWholeImage = MutableStateFlow(true)
    val fitWholeImage = _fitWholeImage.asStateFlow()

    fun toggleFitWholeImage() { _fitWholeImage.value = !_fitWholeImage.value }

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
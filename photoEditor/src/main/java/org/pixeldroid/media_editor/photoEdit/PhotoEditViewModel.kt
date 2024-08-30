package org.pixeldroid.media_editor.photoEdit

import android.graphics.Path
import android.net.Uri
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer

class PhotoEditViewModel: ViewModel() {
    private val _shownView: MutableStateFlow<ShownView> = MutableStateFlow(ShownView.Main)
    val shownView: StateFlow<ShownView> = _shownView

    var drawingHeight: Int = -1
    var drawingWidth: Int = -1
    var previousScaledHeight: Int = -1
    var previousScaledWidth: Int = -1
    var bitmapHeight: Int = -1
    var bitmapWidth: Int = -1

    private val _stickerChosen: MutableStateFlow<Pair<Float, Float>?> = MutableStateFlow(null)
    val stickerChosen: StateFlow<Pair<Float, Float>?> = _stickerChosen.asStateFlow()

    data class PositionedSticker(
        val uri: Uri,
        // Fraction of positioning in image along x axis, 0 is left, 1 is right
        val x: Float,
        // Fraction of positioning in image along y axis, 0 is top, 1 is bottom
        val y: Float
    )
    private val _stickerList: MutableStateFlow<ArrayList<PositionedSticker>> = MutableStateFlow(arrayListOf())
    val stickerList: StateFlow<ArrayList<PositionedSticker>> = _stickerList.asStateFlow()

    enum class ShownView {
        Main, Draw, Text, Sticker,
    }

    private val _sliders: MutableStateFlow<Sliders> = MutableStateFlow(Sliders())
    val sliders: StateFlow<Sliders> = _sliders

    data class Sliders (
        val brightness: Float = BRIGHTNESS_START,
        val contrast: Float = CONTRAST_START,
        val saturation: Float = SATURATION_START,
    ) {
        companion object {
            const val BRIGHTNESS_START = 0f
            const val SATURATION_START = 0f
            const val CONTRAST_START = 0f
        }
    }

    var imageUri: Uri? = null

    // Path of the drawing
    val drawingPath: Path = Path()

    data class PositionedString(
        val string: String,
        // Fraction of positioning in image along x axis, 0 is left, 1 is right
        val x: Float,
        // Fraction of positioning in image along y axis, 0 is top, 1 is bottom
        val y: Float
    )
    val textList = ArrayList<PositionedString>()

    private val _filter: MutableStateFlow<ImagineLayer?> = MutableStateFlow(null)
    val filter: StateFlow<ImagineLayer?> = _filter


    fun startDraw() {
        _shownView.value = ShownView.Draw
    }

    fun startText() {
        _shownView.value = ShownView.Text
    }

    fun startStickers() {
        _shownView.value = ShownView.Sticker
    }

    fun showMain() {
        _shownView.value = ShownView.Main
    }

    private fun resetSliders() {
        _sliders.value = Sliders()
    }

    fun onBrightnessChange(brightness: Float) {
        _sliders.value = _sliders.value.copy(brightness = brightness)
    }

    fun onContrastChange(contrast: Float) {
        _sliders.value = _sliders.value.copy(contrast = contrast)
    }

    fun onSaturationChange(saturation: Float) {
        _sliders.value = _sliders.value.copy(saturation = saturation)
    }

    fun reset() {
        resetSliders()
        filterSelected(null)
        drawingPath.reset()
        textList.clear()
        stickerList.value.clear()
    }

    fun filterSelected(filter: ImagineLayer?) {
        _filter.value = filter
    }

    fun chooseSticker(x: Float, y: Float) {
        _stickerChosen.value = Pair(x, y)
    }

    fun resetSticker() {
        _stickerChosen.value = null
    }

    fun addTextAt(text: String, x: Float, y: Float) {
        textList.add(PositionedString(text, x, y))
    }

    fun addStickerAt(sticker: Uri, x: Float, y: Float) {
        stickerList.value.add(PositionedSticker(sticker, x, y))
    }
}

class PhotoEditViewModelFactory:
    AbstractSavedStateViewModelFactory() {
    override fun <T : ViewModel> create(
        key: String, modelClass: Class<T>, handle: SavedStateHandle
    ): T {
        return modelClass.getConstructor().newInstance()
    }
}

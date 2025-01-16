package org.pixeldroid.media_editor.photoEdit

import android.graphics.Path
import android.net.Uri
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
    private val _stickerList: MutableStateFlow<List<PositionedSticker>> = MutableStateFlow(arrayListOf())
    val stickerList: StateFlow<List<PositionedSticker>> = _stickerList.asStateFlow()

    enum class ShownView {
        Main, Draw, Text, Sticker,
    }

    private val _sliders: MutableStateFlow<Sliders> = MutableStateFlow(Sliders())
    val sliders: StateFlow<Sliders> = _sliders

    @OptIn(FlowPreview::class)
    val slidersChange = sliders.debounce(750).map { Change.SlidersChange(it) }

    init {
        viewModelScope.launch {
            slidersChange.collect { doChange(it) }
        }
    }

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

    sealed class Change() {
        data class Draw(
            val path: Path
        ): Change()

        data class PositionText(
            val text: String,
            val x: Float,
            val y: Float,
        ) : Change()

        data class PositionSticker(
            val sticker: Uri,
            val x: Float,
            val y: Float,
        ) : Change()

        data class SelectFilter(
            val filter: ImagineLayer?
        ): Change()

        data class SlidersChange(
            val sliders: Sliders
        ): Change()

        data class CropChange(
            val newImage: Uri
        ): Change()
    }

    var changes: List<Change> = emptyList()
    var redoChanges: List<Change> = emptyList()

    var imageUri: Uri? = null
        get() = field ?: initialUri
    var initialUri: Uri? = null

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

    fun startDraw() { _shownView.value = ShownView.Draw }

    fun startText() { _shownView.value = ShownView.Text }

    fun startStickers() { _shownView.value = ShownView.Sticker }

    fun showMain() { _shownView.value = ShownView.Main }

    private fun resetSliders() { _sliders.value = Sliders() }

    // Harder to track change, need debounce
    fun onBrightnessChange(brightness: Float) {
        _sliders.value = _sliders.value.copy(brightness = brightness)
    }
    // Harder to track change, need debounce
    fun onContrastChange(contrast: Float) {
        _sliders.value = _sliders.value.copy(contrast = contrast)
    }
    // Harder to track change, need debounce
    fun onSaturationChange(saturation: Float) {
        _sliders.value = _sliders.value.copy(saturation = saturation)
    }

    fun reset() {
        resetSliders()
        filterSelected(null)
        drawingPath.reset()
        textList.clear()
        _stickerList.value = emptyList()
        redoChanges = changes.reversed()
        changes = emptyList()
        imageUri = initialUri
    }

    fun restoreState(changes: List<Change>) {
        val pastSliders = (changes.findLast { it is Change.SlidersChange } as? Change.SlidersChange)?.sliders
        if (pastSliders != null) {
            _sliders.value = pastSliders
            println("Setting sliders to $pastSliders")
        } else {
            println("Resetting sliders")
            resetSliders()
        }

        val filter = (changes.findLast { it is Change.SelectFilter } as? Change.SelectFilter)?.filter
        filterSelected(filter)

        val path = (changes.findLast { it is Change.Draw } as? Change.Draw)?.path
        if (path != null) {
            drawingPath.set(path)
        } else drawingPath.reset()

        changes.filter { it is Change.PositionText ||  it is Change.PositionSticker}.forEach {
            doChange(it)
        }

        val image = (changes.findLast { it is Change.CropChange } as? Change.CropChange)?.newImage
        imageUri = image ?: initialUri
    }

    private fun filterSelected(filter: ImagineLayer?) {
        _filter.value = filter
    }

    fun chooseSticker(x: Float, y: Float) {
        _stickerChosen.value = Pair(x, y)
    }

    fun resetSticker() {
        _stickerChosen.value = null
    }

    private fun addTextAt(text: String, x: Float, y: Float) {
        if (!changes.contains(Change.PositionText(text, x, y)))
            textList.add(PositionedString(text, x, y))
    }

    private fun addStickerAt(sticker: Uri, x: Float, y: Float) {
        _stickerList.value += PositionedSticker(sticker, x, y)
    }

    fun slidersWereChanged(sliders: Sliders): Boolean {
        val lastSliders = (changes.findLast { it is Change.SlidersChange } as? Change.SlidersChange)?.sliders
        return if (lastSliders != null) {
            sliders != lastSliders
        } else {
            sliders != Sliders()
        }
    }

    fun doChange(c: Change, dropRedoHistory: Boolean = true) {
        when(c) {
            is Change.Draw -> drawingPath.set(c.path)
            is Change.PositionSticker -> {
                if (changes.contains(c)) return
                addStickerAt(c.sticker, c.x, c.y)
            }
            is Change.PositionText -> {
                if (changes.contains(c)) return
                addTextAt(c.text, c.x, c.y)
            }
            is Change.SelectFilter -> {
                val same = filter.value == c.filter
                filterSelected(c.filter)
                if (same) return
            }
            is Change.SlidersChange -> {
                if (slidersWereChanged(c.sliders)) _sliders.value = c.sliders
                else return
            }
            is Change.CropChange -> imageUri = c.newImage
        }
        changes = changes + c
        if (dropRedoHistory) redoChanges = emptyList()
        println(changes) //TODO remove
    }

    fun undoChange() {
        val lastChange = changes.lastOrNull()
        if(lastChange != null ) redoChanges = redoChanges + lastChange
        changes = changes.dropLast(1)
        textList.clear()
        _stickerList.value = emptyList()
        restoreState(changes)
    }

    fun redoChange() {
        val firstChange = redoChanges.lastOrNull()
        if(firstChange != null) doChange(firstChange, dropRedoHistory = false)
        redoChanges = redoChanges.dropLast(1)
    }

    fun emitPathChange() {
        doChange(Change.Draw(Path(drawingPath)))
    }
}

class PhotoEditViewModelFactory: AbstractSavedStateViewModelFactory() {
    override fun <T : ViewModel> create(
        key: String, modelClass: Class<T>, handle: SavedStateHandle
    ): T {
        return modelClass.getConstructor().newInstance()
    }
}

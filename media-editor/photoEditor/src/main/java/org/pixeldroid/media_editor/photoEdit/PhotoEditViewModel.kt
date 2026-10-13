package org.pixeldroid.media_editor.photoEdit

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer
import org.pixeldroid.media_editor.photoEdit.overlay.Overlay
import org.pixeldroid.media_editor.photoEdit.overlay.RelativePoint
import org.pixeldroid.media_editor.photoEdit.overlay.StickerItem
import org.pixeldroid.media_editor.photoEdit.overlay.Stroke
import org.pixeldroid.media_editor.photoEdit.overlay.TextItem

class PhotoEditViewModel: ViewModel() {
    private val _shownView: MutableStateFlow<ShownView> = MutableStateFlow(ShownView.Main)
    val shownView: StateFlow<ShownView> = _shownView

    private val _stickerChosen: MutableStateFlow<Pair<Float, Float>?> = MutableStateFlow(null)
    val stickerChosen: StateFlow<Pair<Float, Float>?> = _stickerChosen.asStateFlow()

    /** Drawings, texts and stickers, relative to the image */
    private val _overlay = MutableStateFlow(Overlay())
    val overlay: StateFlow<Overlay> = _overlay.asStateFlow()

    enum class ShownView {
        Main, Draw, Text, Sticker, Crop
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
        val sharpness: Float = SHARPNESS_START,
        val highlights: Float = HIGHLIGHTS_START,
        val shadows: Float = SHADOWS_START,
        val warmth: Float = WARMTH_START,
        val tint: Float = TINT_START,
        val vignette: Float = VIGNETTE_START,
        val exposure: Float = EXPOSURE_START,
        val vibrance: Float = VIBRANCE_START,
        val clarity: Float = CLARITY_START,
        val grain: Float = GRAIN_START,
    ) {
        companion object {
            const val BRIGHTNESS_START = 0f
            const val SATURATION_START = 0f
            const val CONTRAST_START = 0f
            const val SHARPNESS_START = 0f
            const val HIGHLIGHTS_START = 0f
            const val SHADOWS_START = 0f
            const val WARMTH_START = 0f
            const val TINT_START = 0f
            const val VIGNETTE_START = 0f
            const val EXPOSURE_START = 0f
            const val VIBRANCE_START = 0f
            const val CLARITY_START = 0f
            const val GRAIN_START = 0f
        }
    }

    sealed class Change {
        data class AddStroke(val stroke: Stroke) : Change()

        data class AddText(val text: TextItem) : Change()

        data class AddSticker(val sticker: StickerItem) : Change()

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

    private var history = EditHistory<Change>()

    /** Edits in the order they were made (for tests and the "no edits" check) */
    val changes: List<Change> get() = history.changes

    var imageUri: Uri? = null
        get() = field ?: initialUri
    var initialUri: Uri? = null

    private val _filter: MutableStateFlow<ImagineLayer?> = MutableStateFlow(null)
    val filter: StateFlow<ImagineLayer?> = _filter

    fun startDraw() { _shownView.value = ShownView.Draw }

    fun startText() { _shownView.value = ShownView.Text }

    fun startStickers() { _shownView.value = ShownView.Sticker }

    fun startCrop() {
        _shownView.value = ShownView.Crop
        _shownView.value = ShownView.Main
    }

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
    fun onSharpnessChange(sharpness: Float) {
        _sliders.value = _sliders.value.copy(sharpness = sharpness)
    }
    fun onHighlightsChange(highlights: Float) {
        _sliders.value = _sliders.value.copy(highlights = highlights)
    }
    fun onShadowsChange(shadows: Float) {
        _sliders.value = _sliders.value.copy(shadows = shadows)
    }
    fun onWarmthChange(warmth: Float) {
        _sliders.value = _sliders.value.copy(warmth = warmth)
    }
    fun onTintChange(tint: Float) {
        _sliders.value = _sliders.value.copy(tint = tint)
    }
    fun onVignetteChange(vignette: Float) {
        _sliders.value = _sliders.value.copy(vignette = vignette)
    }
    fun onExposureChange(exposure: Float) {
        _sliders.value = _sliders.value.copy(exposure = exposure)
    }
    fun onVibranceChange(vibrance: Float) {
        _sliders.value = _sliders.value.copy(vibrance = vibrance)
    }
    fun onClarityChange(clarity: Float) {
        _sliders.value = _sliders.value.copy(clarity = clarity)
    }
    fun onGrainChange(grain: Float) {
        _sliders.value = _sliders.value.copy(grain = grain)
    }

    /** Undoes everything; it can still be redone step by step */
    fun reset() {
        history = history.undoAll()
        restoreState(history.changes)
    }

    /** Sets every part of the edit to what [changes] result in */
    private fun restoreState(changes: List<Change>) {
        val pastSliders = (changes.findLast { it is Change.SlidersChange } as? Change.SlidersChange)?.sliders
        if (pastSliders != null) {
            _sliders.value = pastSliders
        } else {
            resetSliders()
        }

        val filter = (changes.findLast { it is Change.SelectFilter } as? Change.SelectFilter)?.filter
        filterSelected(filter)

        _overlay.value = changes.fold(Overlay()) { overlay, change ->
            when (change) {
                is Change.AddStroke -> overlay + change.stroke
                is Change.AddText -> overlay + change.text
                is Change.AddSticker -> overlay + change.sticker
                else -> overlay
            }
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

    /** A finished line, points relative to the image */
    fun addStroke(points: List<RelativePoint>) {
        if (points.isNotEmpty()) doChange(Change.AddStroke(Stroke(points)))
    }

    /** Text with its baseline starting at (x, y), relative to the image */
    fun addText(text: String, x: Float, y: Float) {
        if (text.isNotBlank()) doChange(Change.AddText(TextItem(text, x, y)))
    }

    /** A sticker centred at (x, y), relative to the image */
    fun addSticker(uri: Uri, x: Float, y: Float) {
        doChange(Change.AddSticker(StickerItem(uri.toString(), x, y)))
    }

    private fun slidersWereChanged(sliders: Sliders): Boolean {
        val lastSliders = (changes.findLast { it is Change.SlidersChange } as? Change.SlidersChange)?.sliders
        return if (lastSliders != null) {
            sliders != lastSliders
        } else {
            sliders != Sliders()
        }
    }

    /** Applies a new edit and records it */
    fun doChange(c: Change) {
        when (c) {
            is Change.AddStroke -> _overlay.value += c.stroke
            is Change.AddText -> _overlay.value += c.text
            is Change.AddSticker -> _overlay.value += c.sticker
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
        history = history.push(c)
    }

    fun undoChange() {
        history = history.undo()
        restoreState(history.changes)
    }

    fun redoChange() {
        val (_, redone) = history.redo() ?: return
        history = redone
        restoreState(history.changes)
    }

}

class PhotoEditViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = PhotoEditViewModel() as T
}

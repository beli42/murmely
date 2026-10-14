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
import org.pixeldroid.media_editor.photoEdit.overlay.ToolStyle

class PhotoEditViewModel: ViewModel() {
    private val _shownView: MutableStateFlow<ShownView> = MutableStateFlow(ShownView.Main)
    val shownView: StateFlow<ShownView> = _shownView

    private val _stickerChosen: MutableStateFlow<Pair<Float, Float>?> = MutableStateFlow(null)
    val stickerChosen: StateFlow<Pair<Float, Float>?> = _stickerChosen.asStateFlow()

    /** Drawings, texts and stickers, relative to the image */
    private val _overlay = MutableStateFlow(Overlay())
    val overlay: StateFlow<Overlay> = _overlay.asStateFlow()

    /** Colour and size for new lines and texts (not part of the history) */
    private val _toolStyle = MutableStateFlow(ToolStyle())
    val toolStyle: StateFlow<ToolStyle> = _toolStyle.asStateFlow()

    fun onToolStyleChange(style: ToolStyle) {
        _toolStyle.value = style
    }

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

    sealed class Change {
        data class AddStroke(val stroke: Stroke) : Change()

        data class AddText(val text: TextItem) : Change()

        data class AddSticker(val sticker: StickerItem) : Change()

        data class MoveText(val id: Long, val x: Float, val y: Float) : Change()

        /** Blank text removes it */
        data class EditText(val id: Long, val text: String) : Change()

        data class MoveSticker(val id: Long, val x: Float, val y: Float) : Change()

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
        set(value) {
            field = value
            _canUndo.value = value.canUndo
            _canRedo.value = value.canRedo
        }

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    /** Edits in the order they were made (for tests and the "no edits" check) */
    val changes: List<Change> get() = history.changes

    private val _imageUri = MutableStateFlow<Uri?>(null)
    /** The image being edited, observable (it changes when cropping) */
    val currentImage: StateFlow<Uri?> = _imageUri.asStateFlow()

    var imageUri: Uri?
        get() = _imageUri.value ?: initialUri
        set(value) { _imageUri.value = value ?: initialUri }

    var initialUri: Uri? = null
        set(value) {
            field = value
            if (_imageUri.value == null) _imageUri.value = value
        }

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

    /** Turns a tool (draw, text, sticker) on, or off if it is already on */
    fun toggleTool(tool: ShownView) {
        _shownView.value = if (_shownView.value == tool) ShownView.Main else tool
    }

    private fun resetSliders() { _sliders.value = Sliders() }

    /** A slider moved; recorded in the history after it stopped for a moment (see slidersChange) */
    fun onAdjustmentChange(adjustment: Adjustment, value: Float) {
        _sliders.value = _sliders.value.with(adjustment, value)
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

        _overlay.value = changes.fold(Overlay()) { overlay, change -> applyToOverlay(overlay, change) }

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

    /** The overlay after [change] (other kinds of changes leave it as it is) */
    private fun applyToOverlay(overlay: Overlay, change: Change): Overlay = when (change) {
        is Change.AddStroke -> overlay + change.stroke
        is Change.AddText -> overlay + change.text
        is Change.AddSticker -> overlay + change.sticker
        is Change.MoveText -> overlay.moveText(change.id, change.x, change.y)
        is Change.EditText -> overlay.editText(change.id, change.text)
        is Change.MoveSticker -> overlay.moveSticker(change.id, change.x, change.y)
        else -> overlay
    }

    /** A finished line, points relative to the image, in the current colour and width */
    fun addStroke(points: List<RelativePoint>) {
        if (points.isEmpty()) return
        val style = _toolStyle.value
        doChange(Change.AddStroke(Stroke(points, style.strokeWidth, style.color)))
    }

    /** Text with its baseline starting at (x, y), relative to the image, in the current colour and size */
    fun addText(text: String, x: Float, y: Float) {
        if (text.isBlank()) return
        val style = _toolStyle.value
        doChange(Change.AddText(TextItem(text, x, y, style.textSize, style.color, _overlay.value.nextId())))
    }

    /** A sticker centred at (x, y), relative to the image; [aspect] = height / width of the sticker image */
    fun addSticker(uri: Uri, x: Float, y: Float, aspect: Float = 1f) {
        doChange(Change.AddSticker(StickerItem(uri.toString(), x, y, aspect = aspect, id = _overlay.value.nextId())))
    }

    fun moveText(id: Long, x: Float, y: Float) = doChange(Change.MoveText(id, x, y))

    /** Changes a text; blank text removes it */
    fun editText(id: Long, text: String) = doChange(Change.EditText(id, text))

    fun moveSticker(id: Long, x: Float, y: Float) = doChange(Change.MoveSticker(id, x, y))

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
            is Change.AddStroke, is Change.AddText, is Change.AddSticker,
            is Change.MoveText, is Change.EditText, is Change.MoveSticker ->
                _overlay.value = applyToOverlay(_overlay.value, c)
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

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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.pixeldroid.media_editor.photoEdit.crop.CropSettings
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.CenterUse
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ConfiguredFilter
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.FilterSettings
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.base
import org.pixeldroid.media_editor.photoEdit.panel.sameFilter
import org.pixeldroid.media_editor.photoEdit.overlay.Overlay
import org.pixeldroid.media_editor.photoEdit.overlay.RelativePoint
import org.pixeldroid.media_editor.photoEdit.overlay.StickerItem
import org.pixeldroid.media_editor.photoEdit.overlay.Stroke
import org.pixeldroid.media_editor.photoEdit.overlay.TextItem
import org.pixeldroid.media_editor.photoEdit.overlay.ToolStyle

class PhotoEditViewModel: ViewModel() {
    private val _shownView: MutableStateFlow<ShownView> = MutableStateFlow(ShownView.Main)
    val shownView: StateFlow<ShownView> = _shownView

    private val _selectedSticker = MutableStateFlow<Long?>(null)

    /** The sticker with the frame for scaling and turning (null: none) */
    val selectedSticker: StateFlow<Long?> = _selectedSticker.asStateFlow()

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

    /**
     * Settings of the filter being tried while they are being changed (slider, dragging the
     * centre), with the filter they belong to; recorded after they stopped for a moment
     */
    private val pendingFilterSettings = MutableStateFlow<Pair<ImagineLayer, FilterSettings>?>(null)

    @OptIn(FlowPreview::class)
    private val filterSettingsChange = pendingFilterSettings.filterNotNull().debounce(750)

    init {
        viewModelScope.launch {
            slidersChange.collect { doChange(it) }
        }
        viewModelScope.launch {
            filterSettingsChange.collect { (filter, settings) ->
                // Only if that filter is still the one being tried
                if (_filter.value?.base === filter) doChange(Change.FilterSettingsChange(settings))
            }
        }
    }

    sealed class Change {
        /** The filter being tried becomes permanent; the next one is tried on top of it */
        data class ApplyFilter(val filter: ImagineLayer) : Change()

        data class AddStroke(val stroke: Stroke) : Change()

        data class AddText(val text: TextItem) : Change()

        data class AddSticker(val sticker: StickerItem) : Change()

        data class MoveText(val id: Long, val x: Float, val y: Float) : Change()

        /** Blank text removes it */
        data class EditText(val id: Long, val text: String) : Change()

        data class MoveSticker(val id: Long, val x: Float, val y: Float) : Change()
        /** A sticker moved, scaled and/or turned */
        data class TransformSticker(val id: Long, val x: Float, val y: Float, val width: Float, val rotation: Float) : Change()

        /** A filter to try (null: none), with its settings */
        data class SelectFilter(
            val filter: ImagineLayer?,
            val settings: FilterSettings = FilterSettings.DEFAULT,
        ): Change()

        /** New strength or centre of the filter being tried */
        data class FilterSettingsChange(
            val settings: FilterSettings
        ): Change()

        data class SlidersChange(
            val sliders: Sliders
        ): Change()

        /** The image was cropped and/or turned: [newImage] is the result, made from the original */
        data class CropChange(
            val newImage: Uri,
            val settings: CropSettings = CropSettings(),
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

    /** Filter being tried (preview) with its settings, on top of the applied ones */
    private val _filter: MutableStateFlow<ConfiguredFilter?> = MutableStateFlow(null)
    val filter: StateFlow<ConfiguredFilter?> = _filter

    /** The centre (or mirror line) of the filter being tried was tapped or dragged to (x, y) */
    fun onFilterCenterChange(x: Float, y: Float) {
        val current = _filter.value ?: return
        onFilterSettingsChange(current.settings.copy(centerX = x, centerY = y))
    }

    /**
     * New strength or centre of the filter being tried: shown right away, recorded in the history
     * after it stopped changing for a moment. Values are limited to what the filter supports.
     */
    fun onFilterSettingsChange(settings: FilterSettings) {
        val current = _filter.value ?: return
        val filter = current.filter
        val limited = settings.copy(
            strength = settings.strength.coerceIn(0f, filter.strengthMode.max),
            centerX = if (filter.centerUse == CenterUse.NONE) 0.5f else settings.centerX.coerceIn(0f, 1f),
            centerY = if (filter.centerUse == CenterUse.NONE) 0.5f else settings.centerY.coerceIn(0f, 1f),
        )
        if (limited == current.settings) return
        _filter.value = ConfiguredFilter(filter, limited)
        pendingFilterSettings.value = filter to limited
    }

    /** Filters applied one after the other, in that order */
    private val _appliedFilters = MutableStateFlow<List<ImagineLayer>>(emptyList())
    val appliedFilters: StateFlow<List<ImagineLayer>> = _appliedFilters.asStateFlow()

    /** Applies the filter being tried; then another one can be tried on top of it */
    fun applySelectedFilter() {
        val filter = _filter.value ?: return
        // Settings still waiting to be recorded: record them first, so undoing the apply
        // brings back the filter as it was applied
        pendingFilterSettings.value?.let { (pending, settings) ->
            if (pending === filter.filter) doChange(Change.FilterSettingsChange(settings))
        }
        doChange(Change.ApplyFilter(filter))
    }

    fun startDraw() { _shownView.value = ShownView.Draw }

    fun startText() { _shownView.value = ShownView.Text }

    fun startStickers() { _shownView.value = ShownView.Sticker }

    /** Back to the photo: finishes drawing, text, stickers; leaving the crop discards it */
    fun showMain() {
        _shownView.value = ShownView.Main
        _cropDraft.value = null
        _selectedSticker.value = null
    }

    private val _cropDraft = MutableStateFlow<CropSettings?>(null)

    /** The crop being made while cropping (null otherwise), not applied yet */
    val cropDraft: StateFlow<CropSettings?> = _cropDraft.asStateFlow()

    /** The crop of the image as it is now (none: [CropSettings] of the whole image) */
    val recordedCrop: CropSettings
        get() = changes.filterIsInstance<Change.CropChange>().lastOrNull()?.settings ?: CropSettings()

    /** Starts cropping, from the current crop (it can be changed again) */
    fun startCrop() {
        _cropDraft.value = recordedCrop
        _shownView.value = ShownView.Crop
    }

    /** The crop being made changed (frame dragged, shape chosen, turned) */
    fun updateCrop(settings: CropSettings) {
        if (_cropDraft.value != null) _cropDraft.value = settings
    }

    /** The crop is applied: [image] is the cropped original */
    fun finishCrop(image: Uri, settings: CropSettings) {
        if (settings != recordedCrop) doChange(Change.CropChange(image, settings))
        showMain()
    }

    private val _selectedTab = MutableStateFlow(EditorTab.FILTERS)
    /** Tab shown below the photo */
    val selectedTab: StateFlow<EditorTab> = _selectedTab.asStateFlow()

    /**
     * Shows [tab]. Leaving the Edit tab finishes drawing, text or stickers, so touching the photo
     * doesn't draw while adjusting sliders or filters.
     */
    fun selectTab(tab: EditorTab) {
        _selectedTab.value = tab
        if (tab != EditorTab.EDIT) showMain()
    }

    /** Turns a tool (draw, text, sticker) on, or off if it is already on */
    fun toggleTool(tool: ShownView) {
        _shownView.value = if (_shownView.value == tool) ShownView.Main else tool
        _selectedSticker.value = null
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

        _appliedFilters.value = changes.filterIsInstance<Change.ApplyFilter>().map { it.filter }
        pendingFilterSettings.value = null
        filterSelected(triedFilter(changes))

        _overlay.value = changes.fold(Overlay()) { overlay, change -> applyToOverlay(overlay, change) }

        val image = (changes.findLast { it is Change.CropChange } as? Change.CropChange)?.newImage
        imageUri = image ?: initialUri
    }

    private fun filterSelected(filter: ConfiguredFilter?) {
        _filter.value = filter
    }

    /**
     * The filter being tried after [changes]: the last one chosen since the last apply, with the
     * last settings recorded for it since it was chosen
     */
    private fun triedFilter(changes: List<Change>): ConfiguredFilter? {
        val sinceApply = changes.drop(changes.indexOfLast { it is Change.ApplyFilter } + 1)
        val selectIndex = sinceApply.indexOfLast { it is Change.SelectFilter }
        if (selectIndex < 0) return null
        val select = sinceApply[selectIndex] as Change.SelectFilter
        val filter = select.filter ?: return null
        val settings = sinceApply.drop(selectIndex + 1)
            .filterIsInstance<Change.FilterSettingsChange>().lastOrNull()?.settings ?: select.settings
        return ConfiguredFilter(filter.base, settings)
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
        is Change.TransformSticker -> overlay.transformSticker(change.id, change.x, change.y, change.width, change.rotation)
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

    /**
     * A sticker centred at (x, y), relative to the image; [aspect] = height / width of the sticker
     * image. It is selected, so it can be moved, scaled and turned right away.
     */
    fun addSticker(uri: Uri, x: Float, y: Float, aspect: Float = 1f) = addSticker(uri.toString(), x, y, aspect)

    /** See [addSticker]; [uri] as a string, e.g. a built-in sticker (StickerCatalog.uri) */
    fun addSticker(uri: String, x: Float = 0.5f, y: Float = 0.5f, aspect: Float = 1f) {
        val id = _overlay.value.nextId()
        doChange(Change.AddSticker(StickerItem(uri, x, y, aspect = aspect, id = id)))
        _selectedSticker.value = id
    }

    fun selectSticker(id: Long?) {
        _selectedSticker.value = id
    }

    /** Moves, scales and turns a sticker (one step in the history) */
    fun transformSticker(sticker: StickerItem) =
        doChange(Change.TransformSticker(sticker.id, sticker.x, sticker.y, sticker.width, sticker.rotation))

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
            is Change.MoveText, is Change.EditText, is Change.MoveSticker, is Change.TransformSticker ->
                _overlay.value = applyToOverlay(_overlay.value, c)
            is Change.ApplyFilter -> {
                pendingFilterSettings.value = null
                _appliedFilters.value += c.filter
                filterSelected(null)
            }
            is Change.SelectFilter -> {
                // Choosing the filter being tried again keeps its settings
                if (sameFilter(_filter.value?.base, c.filter?.base)) return
                pendingFilterSettings.value = null
                filterSelected(c.filter?.let { ConfiguredFilter(it.base, c.settings) })
            }
            is Change.FilterSettingsChange -> {
                val current = _filter.value ?: return
                // Already recorded (e.g. changed and changed back)
                if (c.settings == triedFilter(changes)?.settings) return
                filterSelected(ConfiguredFilter(current.filter, c.settings))
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

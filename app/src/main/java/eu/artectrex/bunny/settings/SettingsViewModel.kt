package eu.artectrex.bunny.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import eu.artectrex.bunny.SaveLocationText
import eu.artectrex.bunny.SaveSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.pixeldroid.media_editor.photoEdit.EditorLayout
import org.pixeldroid.media_editor.common.CLICK_ZOOM_DEFAULT

/** Everything the settings screen shows */
data class SettingsUiState(
    /** Readable save location, e.g. "Pictures/Murmely" */
    val location: String,
    /** "png" or "jpeg" */
    val format: String,
    val jpegQuality: Int,
    /** Percent of the width for the photo in landscape */
    val landscapePhotoPercent: Int = EditorLayout.LANDSCAPE_PHOTO_DEFAULT,
    /** Click zoom: how long the photo is held (ms), 0: off */
    val clickZoomMs: Int = CLICK_ZOOM_DEFAULT,
) {
    val isJpeg get() = format == FORMAT_JPEG

    companion object {
        const val FORMAT_PNG = "png"
        const val FORMAT_JPEG = "jpeg"
    }
}

/** State and actions of the settings screen; the values are stored by [SaveSettings] */
class SettingsViewModel(private val app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(load())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private fun load() = SettingsUiState(
        location = SaveSettings.describe(app),
        format = SaveSettings.format(app),
        jpegQuality = SaveSettings.jpegQuality(app),
        landscapePhotoPercent = SaveSettings.landscapePhotoPercent(app),
        clickZoomMs = SaveSettings.clickZoomMs(app),
    )

    fun onFolderChosen(uri: Uri) {
        SaveSettings.chooseFolder(app, uri)
        _state.update { it.copy(location = SaveSettings.describe(app)) }
    }

    fun onFormatChange(format: String) {
        SaveSettings.setFormat(app, format)
        _state.update { it.copy(format = format) }
    }

    /** While the slider moves: only the shown value changes */
    fun onQualityChange(quality: Int) {
        _state.update { it.copy(jpegQuality = SaveLocationText.clampQuality(quality)) }
    }

    /** When the slider is released: the value is stored */
    fun onQualityChangeFinished() {
        SaveSettings.setJpegQuality(app, _state.value.jpegQuality)
    }

    /** Like the quality: shown while the slider moves, stored when it is released */
    fun onLandscapePhotoChange(percent: Int) {
        _state.update { it.copy(landscapePhotoPercent = percent.coerceIn(EditorLayout.LANDSCAPE_PHOTO_MIN, EditorLayout.LANDSCAPE_PHOTO_MAX)) }
    }

    fun onClickZoomChange(ms: Int) {
        _state.update { it.copy(clickZoomMs = ms) }
    }

    fun onClickZoomChangeFinished() {
        SaveSettings.setClickZoomMs(app, _state.value.clickZoomMs)
    }

    fun onLandscapePhotoChangeFinished() {
        SaveSettings.setLandscapePhotoPercent(app, _state.value.landscapePhotoPercent)
    }
}

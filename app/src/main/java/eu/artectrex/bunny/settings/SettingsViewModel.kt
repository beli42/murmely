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

/** Everything the settings screen shows */
data class SettingsUiState(
    /** Readable save location, e.g. "Pictures/Murmely" */
    val location: String,
    /** "png" or "jpeg" */
    val format: String,
    val jpegQuality: Int,
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
}

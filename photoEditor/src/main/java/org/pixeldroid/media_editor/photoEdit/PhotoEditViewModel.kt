package org.pixeldroid.media_editor.photoEdit

import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PhotoEditViewModel: ViewModel() {
    private val _shownView: MutableStateFlow<ShownView> = MutableStateFlow(ShownView.Main)

    val shownView: StateFlow<ShownView> = _shownView

    enum class ShownView {
        Main, Draw, Text, Sticker,
    }

    fun startDraw() {
        _shownView.value = ShownView.Draw
    }

    fun showMain() {
        _shownView.value = ShownView.Main
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

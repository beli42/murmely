package org.pixeldroid.media_editor.photoEdit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.pixeldroid.media_editor.common.ui.BunnyTheme
import org.pixeldroid.media_editor.photoEdit.panel.AdjustmentPanel

/** "Sliders" tab of the photo editor (Compose content) */
class SliderFragment : Fragment() {

    private val model: PhotoEditViewModel by activityViewModels { PhotoEditViewModelFactory() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                BunnyTheme {
                    val sliders by model.sliders.collectAsStateWithLifecycle()
                    AdjustmentPanel(sliders, onChange = model::onAdjustmentChange)
                }
            }
        }
}

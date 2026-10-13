package org.pixeldroid.media_editor.photoEdit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import org.pixeldroid.media_editor.common.ui.BunnyTheme
import org.pixeldroid.media_editor.photoEdit.panel.ToolsPanel

/** "Edit" tab of the photo editor: draw, text, stickers, crop (Compose content) */
class DrawingOnTopFragment : Fragment() {

    private val model: PhotoEditViewModel by activityViewModels { PhotoEditViewModelFactory() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                BunnyTheme {
                    ToolsPanel(
                        onDraw = model::startDraw,
                        onText = model::startText,
                        onStickers = model::startStickers,
                        onCrop = model::startCrop,
                    )
                }
            }
        }
}

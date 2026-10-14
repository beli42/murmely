package org.pixeldroid.media_editor.photoEdit

import android.content.res.Configuration
import org.pixeldroid.media_editor.photoEdit.panel.FilterPanel
import org.pixeldroid.media_editor.photoEdit.imagine.core.ImagineView
import org.pixeldroid.media_editor.common.ui.BunnyTheme
import kotlin.coroutines.resume
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import android.graphics.Bitmap
import android.os.Bundle
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.pixeldroid.media_editor.photoEdit.LogViewActivity.Companion.launchLogView
import org.pixeldroid.media_editor.photoEdit.customFilters.CustomFilter
import org.pixeldroid.media_editor.photoEdit.customFilters.CustomLayer
import org.pixeldroid.media_editor.photoEdit.customFilters.DatabaseBuilder
import org.pixeldroid.media_editor.photoEdit.databinding.DialogCustomFilterBinding
import org.pixeldroid.media_editor.photoEdit.imagine.UriImageProvider
import org.pixeldroid.media_editor.photoEdit.imagine.core.ImagineEngine
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.BlackWhiteLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.ElsaLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.FrostLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.MarsLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.NegativeLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.RandLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.SepiaLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.VintageLayer

/**
 * "Filters" tab: the filters with thumbnails (Compose content). The thumbnails are rendered by a
 * second, tiny engine from the image being edited; they are rendered again when the image
 * changes (crop) or the custom filters change.
 */
class FilterListFragment : Fragment() {

    private val model: PhotoEditViewModel by activityViewModels { PhotoEditViewModelFactory() }

    private val predefinedLayers: List<ImagineLayer> = listOf(
        ElsaLayer(),
        VintageLayer(),
        MarsLayer(),
        FrostLayer(),
        SepiaLayer(),
        BlackWhiteLayer(),
        RandLayer(),
        NegativeLayer(),
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { BunnyTheme { Filters() } }
        }

    @Composable
    private fun Filters() {
        val context = LocalContext.current
        val customFilters by remember { DatabaseBuilder.getInstance(context).filtersDao().getAll() }
            .collectAsStateWithLifecycle(initialValue = null)
        val image by model.currentImage.collectAsStateWithLifecycle()
        val selected by model.filter.collectAsStateWithLifecycle()
        val applied by model.appliedFilters.collectAsStateWithLifecycle()
        val customLayers = remember(customFilters) { customFilters.orEmpty().map { it.toLayer() } }
        val filters = predefinedLayers + customLayers

        var engine by remember { mutableStateOf<ImagineEngine?>(null) }
        var thumbnails by remember { mutableStateOf<List<Bitmap?>>(emptyList()) }
        val thumbnailSize = with(LocalDensity.current) { 64.dp.roundToPx() }

        LaunchedEffect(engine, image, customFilters) {
            val thumbnailEngine = engine ?: return@LaunchedEffect
            val uri = image ?: return@LaunchedEffect
            if (customFilters == null) return@LaunchedEffect
            thumbnailEngine.imageProvider = UriImageProvider(context, uri)
            // The engine can only render once its GL surface is ready: retry for a while
            repeat(THUMBNAIL_ATTEMPTS) {
                val rendered = renderThumbnails(thumbnailEngine, thumbnailSize, filters)
                if (rendered != null && rendered.firstOrNull() != null) {
                    thumbnails = rendered
                    // A custom filter whose shader doesn't compile has no thumbnail
                    if (rendered.any { it == null }) showFilterError()
                    return@LaunchedEffect
                }
                delay(THUMBNAIL_RETRY_MS)
            }
        }

        Box {
            // Tiny GL view for the thumbnail engine (must be laid out to render)
            AndroidView(
                factory = { ImagineView(it).also { view -> engine = ImagineEngine(view) } },
                modifier = Modifier.size(1.dp),
            )
            FilterPanel(
                filters = filters,
                thumbnails = thumbnails,
                selected = selected,
                applied = applied,
                vertical = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE,
                onSelect = { model.doChange(PhotoEditViewModel.Change.SelectFilter(it)) },
                onApply = model::applySelectedFilter,
                onLongPress = { filter ->
                    if (filter != null) {
                        showCustomFilterDialog(filter, if (filter is CustomLayer) FilterType.Edit else FilterType.ViewOnly)
                    }
                },
                onCreate = { showCustomFilterDialog(modifyLayer = null, FilterType.New) },
            )
        }
    }

    /** Thumbnails for "none" and every filter, or null if the engine did not render in time */
    private suspend fun renderThumbnails(engine: ImagineEngine, size: Int, filters: List<ImagineLayer>): List<Bitmap?>? =
        withTimeoutOrNull(THUMBNAIL_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                engine.exportBitmap(true, size, filters) { bitmaps ->
                    if (continuation.isActive) continuation.resume(bitmaps.toList())
                }
            }
        }

    private fun showFilterError() {
        val root = view ?: return
        Snackbar.make(root, R.string.filter_error, Snackbar.LENGTH_LONG)
            .setAction(R.string.view_log, launchLogView(requireContext()))
            .show()
    }

    enum class FilterType {
        New, Edit, ViewOnly
    }

    private fun EditText.setReadOnly(originalText: CharSequence) {
        // Hack to make sure the callback isn't triggered by itself changing the text
        var changed = false
        val textWatcher: TextWatcher = doAfterTextChanged {
            @Suppress("KotlinConstantConditions")
            if (changed) return@doAfterTextChanged
            changed = true
            setText(originalText)
            changed = false
        }
        addTextChangedListener(textWatcher)
    }

    private fun showCustomFilterDialog(modifyLayer: ImagineLayer?, type: FilterType) {
        if(type != FilterType.New && modifyLayer == null) return

        val customFilterBinding = DialogCustomFilterBinding.inflate(layoutInflater)

        modifyLayer?.let {
            val name = when (type) {
                FilterType.ViewOnly -> it.name?.let { id -> getString(id) } ?: ""
                else -> it.customName
            }
            customFilterBinding.customFilterTitle.editText?.setText(name)
            customFilterBinding.customFilterShader.editText?.setText(it.source)
        }

        if (type == FilterType.ViewOnly){
            customFilterBinding.customFilterTitleEditText.setReadOnly(getText(modifyLayer?.name!!))
            customFilterBinding.customFilterShaderEditText.setReadOnly(modifyLayer.source)
        }

        val dialogBuilder: MaterialAlertDialogBuilder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(
                getString(
                    when (type) {
                        FilterType.New -> R.string.create_custom_filter
                        FilterType.Edit -> R.string.edit_custom_filter
                        FilterType.ViewOnly -> R.string.view_filter
                    }
                )
            )
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val title =
                    customFilterBinding.customFilterTitle.editText?.text?.toString()?.ifEmpty {
                        getString(
                            R.string.custom_filter
                        )
                    }
                        ?: getString(R.string.custom_filter)
                val code = customFilterBinding.customFilterShader.editText?.text?.toString() ?: ""

                when (type) {
                    FilterType.ViewOnly -> return@setPositiveButton
                    else -> {
                        val db = DatabaseBuilder.getInstance(requireContext())
                        lifecycleScope.launch(Dispatchers.IO) {
                            (modifyLayer as? CustomLayer).let {
                                db.filtersDao().insertOrUpdate(
                                    CustomFilter(title, code, it?.uid ?: 0)
                                )
                            }
                        }
                    }
                }
            }
        if (type != FilterType.ViewOnly) {
            dialogBuilder.setNeutralButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
        }

        if (type == FilterType.Edit) {
            dialogBuilder.setNegativeButtonIcon(
                AppCompatResources.getDrawable(
                    requireContext(),
                    R.drawable.delete
                )
            )
                .setNegativeButton(R.string.delete) { _, _ ->
                    val db = DatabaseBuilder.getInstance(requireContext())
                    lifecycleScope.launch(Dispatchers.IO) {
                        db.filtersDao().delete((modifyLayer as CustomLayer).toFilter())
                    }
                }
        }
        dialogBuilder.setView(customFilterBinding.root).show()
    }

    private companion object {
        const val THUMBNAIL_ATTEMPTS = 20
        const val THUMBNAIL_RETRY_MS = 300L
        const val THUMBNAIL_TIMEOUT_MS = 2000L
    }
}

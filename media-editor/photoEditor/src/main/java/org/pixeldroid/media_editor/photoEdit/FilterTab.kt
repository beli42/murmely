package org.pixeldroid.media_editor.photoEdit

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.EditText
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.pixeldroid.media_editor.photoEdit.customFilters.CustomFilter
import org.pixeldroid.media_editor.photoEdit.customFilters.CustomLayer
import org.pixeldroid.media_editor.photoEdit.customFilters.DatabaseBuilder
import org.pixeldroid.media_editor.photoEdit.databinding.DialogCustomFilterBinding
import org.pixeldroid.media_editor.photoEdit.imagine.UriImageProvider
import org.pixeldroid.media_editor.photoEdit.imagine.core.ImagineEngine
import org.pixeldroid.media_editor.photoEdit.imagine.core.ImagineView
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.BlackWhiteLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.BulgeLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.ElsaLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.FisheyeLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.FrostLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.KaleidoscopeLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.MarsLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.MirrorHorizontalLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.MirrorVerticalLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.MosaicLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.NegativeLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.PinchLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.RandLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.SepiaLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.SwirlLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.TinyPlanetLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.VintageLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.WavesLayer
import org.pixeldroid.media_editor.photoEdit.panel.FilterCategory
import org.pixeldroid.media_editor.photoEdit.panel.FilterPanel
import kotlin.coroutines.resume

/**
 * The built-in filters. Created once per editor screen (see EditorScreen), so the selected
 * filter stays the same object while switching tabs.
 */
class BuiltInFilters {
    val color: List<ImagineLayer> = listOf(
        ElsaLayer(),
        VintageLayer(),
        MarsLayer(),
        FrostLayer(),
        SepiaLayer(),
        BlackWhiteLayer(),
        RandLayer(),
        NegativeLayer(),
    )

    val geometry: List<ImagineLayer> = listOf(
        SwirlLayer(),
        BulgeLayer(),
        PinchLayer(),
        FisheyeLayer(),
        TinyPlanetLayer(),
        KaleidoscopeLayer(),
        MirrorHorizontalLayer(),
        MirrorVerticalLayer(),
        WavesLayer(),
        MosaicLayer(),
    )
}

/**
 * "Filters" tab: the filters with thumbnails. The thumbnails are rendered by a second, tiny
 * engine from the image being edited; they are rendered again when the image changes (crop) or
 * the custom filters change.
 *
 * @param onFilterError a custom filter's shader doesn't compile
 */
@Composable
fun FilterTab(
    model: PhotoEditViewModel,
    builtIn: BuiltInFilters,
    onFilterError: () -> Unit,
) {
    val context = LocalContext.current
    val scope = LocalLifecycleOwner.current.lifecycleScope
    val customFilters by remember { DatabaseBuilder.getInstance(context).filtersDao().getAll() }
        .collectAsStateWithLifecycle(initialValue = null)
    val image by model.currentImage.collectAsStateWithLifecycle()
    val selected by model.filter.collectAsStateWithLifecycle()
    val applied by model.appliedFilters.collectAsStateWithLifecycle()
    val customLayers = remember(customFilters) { customFilters.orEmpty().map { it.toLayer() } }
    // One thumbnail per filter, rendered in this order after the unfiltered one
    val filters = builtIn.color + builtIn.geometry + customLayers
    val groups = mapOf(
        FilterCategory.COLOR to builtIn.color,
        FilterCategory.GEOMETRY to builtIn.geometry,
        FilterCategory.CUSTOM to customLayers,
    )

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
                if (rendered.any { it == null }) onFilterError()
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
            groups = groups,
            thumbnailOf = { filter ->
                if (filter == null) thumbnails.firstOrNull()
                else thumbnails.getOrNull(filters.indexOfFirst { it === filter } + 1)
            },
            selected = selected,
            applied = applied,
            vertical = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE,
            onSelect = { model.doChange(PhotoEditViewModel.Change.SelectFilter(it)) },
            onApply = model::applySelectedFilter,
            onLongPress = { filter ->
                if (filter != null) {
                    showCustomFilterDialog(
                        context, scope, filter,
                        if (filter is CustomLayer) FilterDialogType.Edit else FilterDialogType.ViewOnly,
                    )
                }
            },
            onCreate = { showCustomFilterDialog(context, scope, modifyLayer = null, FilterDialogType.New) },
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

private enum class FilterDialogType { New, Edit, ViewOnly }

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

/** Create, edit or view the shader of a filter (still a View dialog) */
private fun showCustomFilterDialog(
    context: Context,
    scope: CoroutineScope,
    modifyLayer: ImagineLayer?,
    type: FilterDialogType,
) {
    if (type != FilterDialogType.New && modifyLayer == null) return

    val binding = DialogCustomFilterBinding.inflate(LayoutInflater.from(context))

    modifyLayer?.let {
        val name = when (type) {
            FilterDialogType.ViewOnly -> it.name?.let { id -> context.getString(id) } ?: ""
            else -> it.customName
        }
        binding.customFilterTitle.editText?.setText(name)
        binding.customFilterShader.editText?.setText(it.source)
    }

    if (type == FilterDialogType.ViewOnly) {
        binding.customFilterTitleEditText.setReadOnly(context.getText(modifyLayer?.name!!))
        binding.customFilterShaderEditText.setReadOnly(modifyLayer.source)
    }

    val dialogBuilder = MaterialAlertDialogBuilder(context)
        .setTitle(
            when (type) {
                FilterDialogType.New -> R.string.create_custom_filter
                FilterDialogType.Edit -> R.string.edit_custom_filter
                FilterDialogType.ViewOnly -> R.string.view_filter
            }
        )
        .setPositiveButton(android.R.string.ok) { _, _ ->
            if (type == FilterDialogType.ViewOnly) return@setPositiveButton
            val title = binding.customFilterTitle.editText?.text?.toString()
                ?.ifEmpty { context.getString(R.string.custom_filter) }
                ?: context.getString(R.string.custom_filter)
            val code = binding.customFilterShader.editText?.text?.toString() ?: ""
            val db = DatabaseBuilder.getInstance(context)
            scope.launch(Dispatchers.IO) {
                db.filtersDao().insertOrUpdate(CustomFilter(title, code, (modifyLayer as? CustomLayer)?.uid ?: 0))
            }
        }
    if (type != FilterDialogType.ViewOnly) {
        dialogBuilder.setNeutralButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
    }
    if (type == FilterDialogType.Edit) {
        dialogBuilder
            .setNegativeButtonIcon(AppCompatResources.getDrawable(context, R.drawable.delete))
            .setNegativeButton(R.string.delete) { _, _ ->
                val db = DatabaseBuilder.getInstance(context)
                scope.launch(Dispatchers.IO) {
                    db.filtersDao().delete((modifyLayer as CustomLayer).toFilter())
                }
            }
    }
    dialogBuilder.setView(binding.root).show()
}

private const val THUMBNAIL_ATTEMPTS = 20
private const val THUMBNAIL_RETRY_MS = 300L
private const val THUMBNAIL_TIMEOUT_MS = 2000L

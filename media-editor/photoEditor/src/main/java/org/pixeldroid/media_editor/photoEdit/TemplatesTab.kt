package org.pixeldroid.media_editor.photoEdit

import android.content.res.Configuration
import android.graphics.Bitmap
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.pixeldroid.media_editor.photoEdit.crop.CropRenderer
import org.pixeldroid.media_editor.photoEdit.customFilters.DatabaseBuilder
import org.pixeldroid.media_editor.photoEdit.customFilters.SavedTemplate
import org.pixeldroid.media_editor.photoEdit.frame.FrameRenderer
import org.pixeldroid.media_editor.photoEdit.imagine.core.ImagineEngine
import org.pixeldroid.media_editor.photoEdit.imagine.core.ImagineView
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineImageProvider
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.AdjustmentsLayer
import org.pixeldroid.media_editor.photoEdit.panel.TemplateItem
import org.pixeldroid.media_editor.photoEdit.panel.TemplatePanel
import org.pixeldroid.media_editor.photoEdit.templates.TemplateJson
import kotlin.coroutines.resume

/**
 * "Templates" tab: saved looks (sliders, filters, frame), each with a preview of the current
 * photo in that look. The previews are only rendered while this tab is open.
 */
@Composable
fun TemplatesTab(model: PhotoEditViewModel, builtIn: BuiltInFilters) {
    val context = LocalContext.current
    val scope = LocalLifecycleOwner.current.lifecycleScope
    val image by model.currentImage.collectAsStateWithLifecycle()
    val dao = remember { DatabaseBuilder.getInstance(context).templatesDao() }
    val saved by remember { dao.getAll() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val templates = remember(saved) { saved.map { it to TemplateJson.read(it.name, it.json) } }
    var engine by remember { mutableStateOf<ImagineEngine?>(null) }
    var previews by remember { mutableStateOf<Map<Int, Bitmap>>(emptyMap()) }
    val previewSize = with(LocalDensity.current) { 160.dp.roundToPx() }

    LaunchedEffect(engine, image, saved) {
        val previewEngine = engine ?: return@LaunchedEffect
        val uri = image ?: return@LaunchedEffect
        if (templates.isEmpty()) return@LaunchedEffect
        // A small copy of the photo: quick to render every template on
        val small = withContext(Dispatchers.IO) {
            runCatching { CropRenderer.preview(context, uri, previewSize) }.getOrNull()
        } ?: return@LaunchedEffect
        previewEngine.imageProvider = object : ImagineImageProvider {
            override val bitmap: Bitmap get() = small.copy(Bitmap.Config.ARGB_8888, false)
        }
        val rendered = mutableMapOf<Int, Bitmap>()
        for ((entry, template) in templates) {
            val layers = listOf(AdjustmentsLayer().apply { sliders = template.sliders }) + model.templateFilters(template, builtIn)
            val bitmap = renderTemplate(previewEngine, layers) ?: continue
            rendered[entry.uid] = template.frame?.let { FrameRenderer.apply(bitmap, it) } ?: bitmap
            previews = rendered.toMap()
        }
    }

    Box {
        // Tiny GL view for the preview engine (must be laid out to render)
        AndroidView(
            factory = { ImagineView(it).also { view -> engine = ImagineEngine(view) } },
            modifier = Modifier.size(1.dp),
        )
        TemplatePanel(
            templates = templates.map { (entry, _) -> TemplateItem(entry.uid, entry.name, previews[entry.uid]) },
            vertical = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE,
            onSave = { name ->
                val json = TemplateJson.write(model.currentTemplate(name, builtIn))
                scope.launch(Dispatchers.IO) { dao.insert(SavedTemplate(name, json)) }
            },
            onApply = { uid -> templates.firstOrNull { it.first.uid == uid }?.let { model.applyTemplate(it.second, builtIn) } },
            onRename = { uid, name ->
                saved.firstOrNull { it.uid == uid }?.let { scope.launch(Dispatchers.IO) { dao.update(it.copy(name = name)) } }
            },
            onDelete = { uid ->
                saved.firstOrNull { it.uid == uid }?.let { scope.launch(Dispatchers.IO) { dao.delete(it) } }
            },
        )
    }
}

/** [layers] rendered on the engine's (small) image, or null if it did not render in time */
private suspend fun renderTemplate(engine: ImagineEngine, layers: List<ImagineLayer>): Bitmap? {
    repeat(ATTEMPTS) {
        val bitmap = withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine<Bitmap?> { continuation ->
                engine.onBitmap = { if (continuation.isActive) continuation.resume(it) }
                engine.layers = layers
                engine.exportBitmap()
            }
        }
        if (bitmap != null) return bitmap
        // The GL surface may not be ready yet
        delay(RETRY_MS)
    }
    return null
}

private const val ATTEMPTS = 20
private const val RETRY_MS = 300L
private const val TIMEOUT_MS = 3000L

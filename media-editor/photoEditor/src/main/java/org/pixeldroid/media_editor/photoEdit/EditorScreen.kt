package org.pixeldroid.media_editor.photoEdit

import android.content.res.Configuration
import android.view.View
import androidx.annotation.StringRes
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.pixeldroid.media_editor.photoEdit.PhotoEditViewModel.ShownView
import org.pixeldroid.media_editor.photoEdit.imagine.core.ImagineEngine.BitmapDimensions
import org.pixeldroid.media_editor.photoEdit.imagine.core.ImagineView
import org.pixeldroid.media_editor.photoEdit.overlay.ImageFrame
import org.pixeldroid.media_editor.photoEdit.overlay.OverlayRenderer
import org.pixeldroid.media_editor.photoEdit.panel.AdjustmentPanel
import org.pixeldroid.media_editor.photoEdit.panel.HistoryControls
import org.pixeldroid.media_editor.photoEdit.panel.ToolsPanel

/** Share of the screen for the photo (height in portrait, width in landscape) */
private const val PREVIEW_SHARE = 0.7f

/**
 * The photo editor: top bar, the photo with history controls and the compare button, and the
 * tabs below (beside it in landscape).
 *
 * The preview is still made of two Views: [imagePreview] (GL, renders the image with the
 * sliders and filters) and [drawingView] (overlay and its touch input), placed exactly over the
 * shown image.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    model: PhotoEditViewModel,
    imagePreview: ImagineView,
    drawingView: DrawingView,
    bitmapDimensions: BitmapDimensions?,
    saving: Boolean,
    showSaveAs: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onReset: () -> Unit,
    onCompare: (Boolean) -> Unit,
    onCrop: () -> Unit,
    onFilterError: () -> Unit,
) {
    val shownView by model.shownView.collectAsStateWithLifecycle()
    val selectedTab by model.selectedTab.collectAsStateWithLifecycle()
    // Same filter objects while switching tabs
    val builtInFilters = remember { BuiltInFilters() }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(shownView.title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSave, modifier = Modifier.testTag(EditorTags.SAVE)) {
                        Icon(painterResource(R.drawable.ic_save_24dp), stringResource(R.string.save_edit_menu))
                    }
                    if (showSaveAs) {
                        IconButton(onClick = onSaveAs, modifier = Modifier.testTag(EditorTags.SAVE_AS)) {
                            Icon(painterResource(R.drawable.ic_save_as_24dp), stringResource(R.string.save_as))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val preview = @Composable { modifier: Modifier ->
            Preview(model, imagePreview, drawingView, bitmapDimensions, onUndo, onRedo, onReset, onCompare, modifier)
        }
        val tools = @Composable { modifier: Modifier ->
            Column(modifier) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (selectedTab) {
                        EditorTab.FILTERS -> FilterTab(model, builtInFilters, onFilterError)
                        EditorTab.SLIDERS -> {
                            val sliders by model.sliders.collectAsStateWithLifecycle()
                            AdjustmentPanel(sliders, onChange = model::onAdjustmentChange)
                        }
                        EditorTab.EDIT -> {
                            val style by model.toolStyle.collectAsStateWithLifecycle()
                            ToolsPanel(
                                activeTool = shownView,
                                style = style,
                                onToggleTool = model::toggleTool,
                                onCrop = onCrop,
                                onStyleChange = model::onToolStyleChange,
                            )
                        }
                    }
                }
                EditorTabRow(selectedTab, model::selectTab)
            }
        }

        Box(Modifier.fillMaxSize().padding(padding)) {
            if (landscape) {
                Row(Modifier.fillMaxSize()) {
                    preview(Modifier.weight(PREVIEW_SHARE).fillMaxHeight())
                    tools(Modifier.weight(1 - PREVIEW_SHARE).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    preview(Modifier.weight(PREVIEW_SHARE).fillMaxWidth())
                    tools(Modifier.weight(1 - PREVIEW_SHARE).fillMaxWidth())
                }
            }
            if (saving) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter).testTag(EditorTags.PROGRESS))
            }
        }
    }
}

/** Title of the top bar: the active tool, or "Edit" */
@get:StringRes
private val ShownView.title: Int
    get() = when (this) {
        ShownView.Main, ShownView.Crop -> R.string.toolbar_title_edit
        ShownView.Draw -> R.string.draw
        ShownView.Text -> R.string.add_text
        ShownView.Sticker -> R.string.stickers
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTabRow(selected: EditorTab, onSelect: (EditorTab) -> Unit) {
    PrimaryTabRow(selectedTabIndex = selected.ordinal) {
        EditorTab.entries.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                text = { Text(stringResource(tab.label)) },
                modifier = Modifier.testTag(EditorTags.tab(tab)),
            )
        }
    }
}

/**
 * The photo: GL preview, the overlay exactly over the shown image (scaled to fit, bars on two
 * sides), history controls top right and the compare button bottom right.
 */
@Composable
private fun Preview(
    model: PhotoEditViewModel,
    imagePreview: ImagineView,
    drawingView: DrawingView,
    bitmapDimensions: BitmapDimensions?,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onReset: () -> Unit,
    onCompare: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val canUndo by model.canUndo.collectAsStateWithLifecycle()
    val canRedo by model.canRedo.collectAsStateWithLifecycle()
    val overlay by model.overlay.collectAsStateWithLifecycle()
    var size by remember { mutableStateOf(IntSize.Zero) }
    var comparing by remember { mutableStateOf(false) }

    val frame = bitmapDimensions?.takeIf { size.width > 0 && size.height > 0 }?.let {
        ImageFrame(it.width, it.height, size.width, size.height)
    }

    // Redraw the overlay when it changes; sticker images at the size they are shown
    LaunchedEffect(overlay, frame?.shownWidth) {
        drawingView.invalidate()
        val width = frame?.shownWidth ?: return@LaunchedEffect
        if (width > 0) drawingView.setStickerImages(OverlayRenderer.loadStickers(context, overlay, width))
    }

    Box(modifier.onSizeChanged { size = it }) {
        AndroidView(
            factory = { imagePreview.also { (it.parent as? android.view.ViewGroup)?.removeView(it) } },
            modifier = Modifier.fillMaxSize().semantics { contentDescription = context.getString(R.string.image_preview) },
        )
        if (frame != null) {
            AndroidView(
                factory = { drawingView.also { (it.parent as? android.view.ViewGroup)?.removeView(it) } },
                update = { it.visibility = if (comparing) View.INVISIBLE else View.VISIBLE },
                modifier = Modifier
                    .offset { IntOffset(frame.sideBar, frame.topBar) }
                    .size(with(density) { frame.shownWidth.toDp() }, with(density) { frame.shownHeight.toDp() }),
            )
        }
        HistoryControls(
            canUndo, canRedo, onUndo = onUndo, onRedo = onRedo, onReset = onReset,
            modifier = Modifier.align(Alignment.TopEnd),
        )
        CompareButton(
            onCompare = { pressed ->
                comparing = pressed
                onCompare(pressed)
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
        )
    }
}

/** Press and hold to see the original image */
@Composable
private fun CompareButton(onCompare: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.compare_original)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 6.dp,
        modifier = modifier
            .size(40.dp)
            .testTag(EditorTags.COMPARE)
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    onCompare(true)
                    try {
                        tryAwaitRelease()
                    } finally {
                        onCompare(false)
                    }
                })
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.compare), contentDescription = null)
        }
    }
}

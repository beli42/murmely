package org.pixeldroid.media_editor.photoEdit

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.roundToInt
import org.pixeldroid.media_editor.photoEdit.panel.FloatingPosition
import org.pixeldroid.media_editor.photoEdit.panel.Corner
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.gestures.detectDragGestures
import org.pixeldroid.media_editor.photoEdit.overlay.TextEffects
import androidx.compose.ui.unit.em
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Shadow
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import org.pixeldroid.media_editor.photoEdit.crop.CropEditor
import org.pixeldroid.media_editor.photoEdit.frame.FrameGeometry
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import org.pixeldroid.media_editor.photoEdit.canvas.Bounds
import org.pixeldroid.media_editor.photoEdit.frame.PixelRect
import org.pixeldroid.media_editor.photoEdit.frame.FrameRenderer
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.core.graphics.withScale
import org.pixeldroid.media_editor.photoEdit.crop.CropSettings
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.CenterUse
import org.pixeldroid.media_editor.photoEdit.overlay.ImageFrame
import org.pixeldroid.media_editor.photoEdit.overlay.OverlayRenderer
import org.pixeldroid.media_editor.photoEdit.panel.AdjustmentPanel
import org.pixeldroid.media_editor.photoEdit.panel.HistoryControls
import org.pixeldroid.media_editor.photoEdit.panel.LayerState
import org.pixeldroid.media_editor.photoEdit.panel.ToolsPanel
import org.pixeldroid.media_editor.photoEdit.panel.fontFamily

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
    cropping: Boolean,
    onCropDone: (CropSettings) -> Unit,
    onFilterError: () -> Unit,
) {
    val shownView by model.shownView.collectAsStateWithLifecycle()
    val selectedTab by model.selectedTab.collectAsStateWithLifecycle()
    val cropDraft by model.cropDraft.collectAsStateWithLifecycle()
    // Same filter objects while switching tabs
    val builtInFilters = remember { BuiltInFilters() }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        topBar = {
            val crop = cropDraft
            if (crop != null) CropTopBar(
                busy = cropping,
                onCancel = model::showMain,
                onDone = { onCropDone(crop) },
            ) else TopAppBar(
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
                // The tabs on top, right below the photo; what belongs to the tab below them
                EditorTabRow(selectedTab, model::selectTab)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (selectedTab) {
                        EditorTab.FILTERS -> FilterTab(model, builtInFilters, onFilterError)
                        EditorTab.TEMPLATES -> TemplatesTab(model, builtInFilters)
                        EditorTab.SLIDERS -> {
                            val sliders by model.sliders.collectAsStateWithLifecycle()
                            AdjustmentPanel(sliders, onChange = model::onAdjustmentChange)
                        }
                        EditorTab.EDIT -> {
                            val style by model.toolStyle.collectAsStateWithLifecycle()
                            val overlay by model.overlay.collectAsStateWithLifecycle()
                            val selected by model.selectedItem.collectAsStateWithLifecycle()
                            val frameSettings by model.frame.collectAsStateWithLifecycle()
                            val canvasSettings by model.canvas.collectAsStateWithLifecycle()
                            // Layer buttons for the selected text or sticker
                            val layer = selected?.takeIf {
                                shownView == ShownView.Text || shownView == ShownView.Sticker
                            }?.takeIf { it in overlay.layerOrder }?.let { id ->
                                LayerState(overlay.canMoveLayer(id, -1), overlay.canMoveLayer(id, 1))
                            }
                            ToolsPanel(
                                activeTool = shownView,
                                style = style,
                                onToggleTool = model::toggleTool,
                                onCrop = model::startCrop,
                                onStyleChange = model::onToolStyleChange,
                                onAddSticker = { model.addSticker(it) },
                                // Own image: the photo picker, the sticker goes in the middle
                                onStickerFromGallery = { model.chooseSticker(0.5f, 0.5f) },
                                layer = layer,
                                onLayer = model::moveSelectedLayer,
                                frame = frameSettings,
                                onChooseFrame = model::chooseFrame,
                                onAdjustFrame = model::adjustFrame,
                                canvas = canvasSettings,
                                photoSize = model.photoSize,
                                onChooseCanvas = model::chooseCanvas,
                                onAdjustCanvas = model::adjustCanvas,
                            )
                        }
                    }
                }
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
            // Cropping covers the editor instead of replacing it: the GL preview must stay
            // attached, it would lose its image otherwise
            val crop = cropDraft
            val original = model.initialUri
            if (crop != null && original != null) {
                CropEditor(
                    source = original,
                    settings = crop,
                    busy = cropping,
                    onChange = model::updateCrop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** Title of the top bar: the active tool, or "Edit" */
@get:StringRes
private val ShownView.title: Int
    get() = when (this) {
        ShownView.Main -> R.string.toolbar_title_edit
        ShownView.Crop -> R.string.crop
        ShownView.Frame -> R.string.frame
        ShownView.Canvas -> R.string.canvas
        ShownView.Draw -> R.string.draw
        ShownView.Text -> R.string.tool_text
        ShownView.Sticker -> R.string.tool_stickers
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
    // What reset would reset depends on these; collected so the button follows them
    val slidersNow by model.sliders.collectAsStateWithLifecycle()
    val appliedNow by model.appliedFilters.collectAsStateWithLifecycle()
    val canRedo by model.canRedo.collectAsStateWithLifecycle()
    val overlay by model.overlay.collectAsStateWithLifecycle()
    val triedFilter by model.filter.collectAsStateWithLifecycle()
    val selectedTab by model.selectedTab.collectAsStateWithLifecycle()
    var size by remember { mutableStateOf(IntSize.Zero) }
    var comparing by remember { mutableStateOf(false) }
    // Where the undo/redo controls are: a corner, or where they were dragged to
    var corner by rememberSaveable { mutableStateOf(Corner.TOP_RIGHT) }
    var dragged by remember { mutableStateOf<Offset?>(null) }
    var controlsSize by remember { mutableStateOf(IntSize.Zero) }
    // Comparing shows the original photo: without drawings, texts and stickers
    LaunchedEffect(comparing) { drawingView.overlayHidden = comparing }

    // Where everything is on screen: the canvas (photo plus added area) scaled to fit, with
    // the frame around it, and the photo in the canvas. Comparing shows the photo alone
    val frameSettings by model.frame.collectAsStateWithLifecycle()
    val canvasExtension by model.canvas.collectAsStateWithLifecycle()
    val shownFrame = frameSettings.takeIf { !comparing }
    val shownCanvas = canvasExtension?.takeIf { !comparing && !it.isNone }
    val dims = bitmapDimensions
    val canvasSize = dims?.let { shownCanvas?.canvasSize(it.width, it.height) ?: (it.width to it.height) }
    val layout = canvasSize?.takeIf { size.width > 0 && size.height > 0 }?.let { (w, h) ->
        FrameGeometry.layoutOnScreen(shownFrame, w, h, size.width, size.height)
    }
    // layout.photo is the canvas' area on screen; the photo sits in it
    val canvasRect = layout?.photo
    val photoRect = if (layout != null && canvasRect != null && dims != null) {
        val (offsetX, offsetY) = shownCanvas?.photoOffset(dims.width, dims.height) ?: (0 to 0)
        PixelRect(
            canvasRect.left + (offsetX * layout.scale).roundToInt(),
            canvasRect.top + (offsetY * layout.scale).roundToInt(),
            (dims.width * layout.scale).roundToInt(),
            (dims.height * layout.scale).roundToInt(),
        )
    } else null
    // The photo's own area; everything on the photo is placed relative to it
    val frame = photoRect?.let { r -> dims?.let { ImageFrame(it.width, it.height, r.width, r.height) } }
    val photoOffset = photoRect?.let { IntOffset(it.left, it.top) } ?: IntOffset.Zero
    // Drawings, texts and stickers may lie on the whole canvas
    LaunchedEffect(shownCanvas, dims) {
        drawingView.bounds = dims?.let { shownCanvas?.bounds(it.width, it.height) } ?: Bounds.PHOTO
        model.photoSize = dims?.let { it.width to it.height }
    }

    // Frame of the selected sticker: shown in sticker mode only
    val selectedItem by model.selectedItem.collectAsStateWithLifecycle()
    val editingText by model.editingText.collectAsStateWithLifecycle()
    val shownView by model.shownView.collectAsStateWithLifecycle()
    LaunchedEffect(selectedItem, shownView, editingText?.id) { drawingView.invalidate() }
    // The model keeps new texts on the image, for that it needs its shape
    LaunchedEffect(frame?.shownWidth, frame?.shownHeight) {
        frame?.takeIf { it.shownWidth > 0 }?.let { model.imageAspect = it.shownHeight.toFloat() / it.shownWidth }
    }

    // Redraw the overlay when it changes; sticker images at the size they are shown
    LaunchedEffect(overlay, frame?.shownWidth) {
        drawingView.invalidate()
        val width = frame?.shownWidth ?: return@LaunchedEffect
        if (width > 0) drawingView.setStickerImages(OverlayRenderer.loadStickers(context, overlay, width))
    }

    val previewDescription = stringResource(R.string.image_preview)
    Box(modifier.onSizeChanged { size = it }) {
        AndroidView(
            factory = { imagePreview.also { (it.parent as? android.view.ViewGroup)?.removeView(it) } },
            modifier = (
                photoRect?.let {
                    Modifier
                        .offset { photoOffset }
                        .size(with(density) { it.width.toDp() }, with(density) { it.height.toDp() })
                } ?: Modifier.fillMaxSize()
            ).semantics { contentDescription = previewDescription },
        )
        // The added area around the photo (not over it: the photo is a GL surface below)
        if (shownCanvas != null && canvasRect != null && photoRect != null) {
            val color = Color(shownCanvas.color)
            Canvas(
                Modifier
                    .offset { IntOffset(canvasRect.left, canvasRect.top) }
                    .size(with(density) { canvasRect.width.toDp() }, with(density) { canvasRect.height.toDp() })
                    .testTag(EditorTags.CANVAS)
            ) {
                val area = Path().apply {
                    fillType = PathFillType.EvenOdd
                    // this.size: the drawing area (size alone is the preview's measured size)
                    addRect(Rect(0f, 0f, this@Canvas.size.width, this@Canvas.size.height))
                    addRect(
                        Rect(
                            Offset((photoRect.left - canvasRect.left).toFloat(), (photoRect.top - canvasRect.top).toFloat()),
                            Size(photoRect.width.toFloat(), photoRect.height.toFloat()),
                        )
                    )
                }
                drawPath(area, color)
            }
        }
        if (frame != null) {
            AndroidView(
                factory = { drawingView.also { (it.parent as? android.view.ViewGroup)?.removeView(it) } },
                // The whole canvas: items may also lie on the added area
                modifier = canvasRect?.let {
                    Modifier
                        .offset { IntOffset(it.left, it.top) }
                        .size(with(density) { it.width.toDp() }, with(density) { it.height.toDp() })
                } ?: Modifier
                    .offset { photoOffset + IntOffset(frame.sideBar, frame.topBar) }
                    .size(with(density) { frame.shownWidth.toDp() }, with(density) { frame.shownHeight.toDp() }),
            )
        }
        // The frame, drawn like in the saved image (over the photo's edge or around it)
        if (layout != null && shownFrame != null && canvasSize != null) {
            Canvas(
                Modifier
                    .offset { IntOffset(layout.output.left, layout.output.top) }
                    .size(with(density) { layout.output.width.toDp() }, with(density) { layout.output.height.toDp() })
                    .testTag(EditorTags.FRAME)
            ) {
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.withScale(layout.scale, layout.scale) {
                        // Around the canvas (photo plus added area)
                        FrameRenderer.draw(this, shownFrame, canvasSize.first, canvasSize.second)
                    }
                }
            }
        }
        // Centre of the filter being tried: only in the filter tab, so it never gets in the way
        // of drawing, texts or stickers
        val centerFilter = triedFilter?.takeIf { selectedTab == EditorTab.FILTERS && it.centerUse != CenterUse.NONE }
        if (frame != null && centerFilter != null && !comparing) {
            FilterCenter(
                use = centerFilter.centerUse,
                x = centerFilter.settings.centerX,
                y = centerFilter.settings.centerY,
                onMove = model::onFilterCenterChange,
                modifier = Modifier
                    .offset { photoOffset + IntOffset(frame.sideBar, frame.topBar) }
                    .size(with(density) { frame.shownWidth.toDp() }, with(density) { frame.shownHeight.toDp() }),
            )
        }
        // Text typed right on the photo, where it will be
        val edit = editingText
        if (frame != null && edit != null && !comparing) {
            InlineTextField(
                edit = edit,
                frame = frame,
                onChange = model::updateTextEdit,
                onDone = model::finishTextEdit,
                modifier = Modifier
                    .offset { photoOffset + IntOffset(frame.sideBar, frame.topBar) }
                    .size(with(density) { frame.shownWidth.toDp() }, with(density) { frame.shownHeight.toDp() }),
            )
        }
        // Undo, redo, reset, compare: in a corner (tap the handle for the next one, clockwise) or
        // dragged anywhere, so they don't cover what matters on the photo
        val controlsPosition = dragged ?: FloatingPosition.inCorner(
            corner, size.width, size.height, controlsSize.width, controlsSize.height,
            margin = 0,
        ).let { (x, y) -> Offset(x.toFloat(), y.toFloat()) }
        // Where the controls are now, for a drag that starts from a corner
        val latestPosition by rememberUpdatedState(controlsPosition)
        HistoryControls(
            canUndo, canRedo, onUndo = onUndo, onRedo = onRedo, onReset = onReset,
            // Reset works on the area shown: usable when there is something to reset there
            canReset = remember(slidersNow, appliedNow, overlay, triedFilter, selectedTab, shownView, frameSettings, canvasExtension) {
                model.canResetHere()
            },
            // Before / after: press and hold to see the original photo
            onCompare = { pressed ->
                comparing = pressed
                onCompare(pressed)
            },
            modifier = Modifier
                .offset { IntOffset(controlsPosition.x.roundToInt(), controlsPosition.y.roundToInt()) }
                .onSizeChanged { controlsSize = it },
            handleModifier = Modifier
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        corner = corner.next
                        dragged = null
                    })
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, amount ->
                        change.consume()
                        val from = dragged ?: latestPosition
                        val (x, y) = FloatingPosition.clamp(
                            (from.x + amount.x).roundToInt(), (from.y + amount.y).roundToInt(),
                            size.width, size.height, controlsSize.width, controlsSize.height,
                        )
                        dragged = Offset(x.toFloat(), y.toFloat())
                    }
                },
        )
    }
}


/**
 * Marker of a filter's centre (a ring) or mirror line, over the shown image. Tapping or dragging
 * anywhere on the image moves it there; [onMove] gets the position relative to the image.
 */
@Composable
private fun FilterCenter(use: CenterUse, x: Float, y: Float, onMove: (Float, Float) -> Unit, modifier: Modifier = Modifier) {
    val currentOnMove by rememberUpdatedState(onMove)
    val color = MaterialTheme.colorScheme.primary
    Canvas(
        modifier
            .testTag(EditorTags.FILTER_CENTER)
            .pointerInput(Unit) {
                fun report(position: Offset) = currentOnMove(
                    (position.x / size.width).coerceIn(0f, 1f),
                    (position.y / size.height).coerceIn(0f, 1f),
                )
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    report(down.position)
                    drag(down.id) { change ->
                        change.consume()
                        report(change.position)
                    }
                }
            }
    ) {
        val cx = x * size.width
        val cy = y * size.height
        when (use) {
            CenterUse.POINT -> {
                val radius = 14.dp.toPx()
                // White under the colour, visible on dark and light images
                drawRing(Offset(cx, cy), radius, Color.White, 4.dp.toPx())
                drawRing(Offset(cx, cy), radius, color, 2.dp.toPx())
                drawCircle(color, radius = 2.dp.toPx(), center = Offset(cx, cy))
            }
            CenterUse.VERTICAL_AXIS -> drawAxis(Offset(cx, 0f), Offset(cx, size.height), color)
            CenterUse.HORIZONTAL_AXIS -> drawAxis(Offset(0f, cy), Offset(size.width, cy), color)
            CenterUse.NONE -> Unit
        }
    }
}

private fun DrawScope.drawRing(center: Offset, radius: Float, color: Color, width: Float) =
    drawCircle(color, radius = radius, center = center, style = Stroke(width))

private fun DrawScope.drawAxis(start: Offset, end: Offset, color: Color) {
    val dashes = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
    drawLine(Color.White, start, end, strokeWidth = 4.dp.toPx(), pathEffect = dashes)
    drawLine(color, start, end, strokeWidth = 2.dp.toPx(), pathEffect = dashes)
}

/** Top bar while cropping: cancel, apply */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CropTopBar(busy: Boolean, onCancel: () -> Unit, onDone: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.crop)) },
        navigationIcon = {
            IconButton(onClick = onCancel, enabled = !busy, modifier = Modifier.testTag(EditorTags.CROP_CANCEL)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.crop_cancel))
            }
        },
        actions = {
            IconButton(onClick = onDone, enabled = !busy, modifier = Modifier.testTag(EditorTags.CROP_APPLY)) {
                Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.crop_apply))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    )
}

/**
 * Text field for typing a text on the photo: centred where the text will be, in its colour, size
 * and angle, with a blinking cursor. Done with the keyboard's Done key or by touching elsewhere.
 */
@Composable
private fun InlineTextField(
    edit: PhotoEditViewModel.TextEdit,
    frame: ImageFrame,
    onChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // A new field for each text being typed; the cursor starts at the end
    var value by remember(edit.id, edit.x, edit.y) {
        mutableStateOf(TextFieldValue(edit.text, TextRange(edit.text.length)))
    }
    var focused by remember(edit.id, edit.x, edit.y) { mutableStateOf(false) }
    var fieldSize by remember { mutableStateOf(IntSize.Zero) }
    val color = Color(edit.color)
    // Same size as drawn: the text size is relative to the image width
    val fontSize = with(density) { (edit.size * frame.shownWidth).toSp() }
    // Letter spacing, outline and one shadow (the drop shadow if there is one, else the soft
    // one) while typing; the finished text shows all effects
    val e = edit.effects
    val textPx = edit.size * frame.shownWidth
    val outlinePx = e.outline * textPx
    val shadow = when {
        e.dropShadow > 0f -> {
            val angle = Math.toRadians(e.dropShadowAngle.toDouble())
            Shadow(
                Color(e.dropShadowColor),
                Offset((kotlin.math.cos(angle) * e.dropShadow * textPx).toFloat(), (kotlin.math.sin(angle) * e.dropShadow * textPx).toFloat()),
                0f,
            )
        }
        e.shadow > 0f -> Shadow(
            Color(0x80000000),
            Offset(e.shadow * TextEffects.SHADOW_OFFSET * textPx, e.shadow * TextEffects.SHADOW_OFFSET * textPx),
            e.shadow * TextEffects.SHADOW_BLUR * textPx,
        )
        else -> null
    }
    val typedStyle = TextStyle(
        color = color,
        fontSize = fontSize,
        fontFamily = edit.font.fontFamily(),
        textAlign = TextAlign.Center,
        letterSpacing = e.letterSpacing.em,
        shadow = shadow,
    )
    val centreX = edit.x * frame.shownWidth
    val centreY = edit.y * frame.shownHeight

    LaunchedEffect(edit.id, edit.x, edit.y) {
        focus.requestFocus()
        keyboard?.show()
    }
    DisposableEffect(Unit) { onDispose { keyboard?.hide() } }

    Box(modifier) {
        BasicTextField(
            value = value,
            onValueChange = {
                value = it
                onChange(it.text)
            },
            singleLine = true,
            textStyle = typedStyle,
            cursorBrush = SolidColor(color),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            // The outline behind the typed letters, as it will be drawn
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.Center) {
                    if (outlinePx > 0f) {
                        Text(
                            value.text,
                            style = typedStyle.copy(
                                color = Color(edit.effects.outlineColor),
                                drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(width = outlinePx * 2, join = StrokeJoin.Round),
                                shadow = null,
                            ),
                            maxLines = 1,
                        )
                    }
                    inner()
                }
            },
            modifier = Modifier
                // Centred on the text's position, whatever its width
                .offset { IntOffset((centreX - fieldSize.width / 2f).roundToInt(), (centreY - fieldSize.height / 2f).roundToInt()) }
                .onSizeChanged { fieldSize = it }
                .graphicsLayer { rotationZ = edit.rotation }
                .border(1.dp, Color.White.copy(alpha = 0.7f))
                .padding(horizontal = 4.dp)
                .defaultMinSize(minWidth = with(density) { fontSize.toDp() })
                .focusRequester(focus)
                .onFocusChanged {
                    // Touching elsewhere ends typing
                    if (focused && !it.isFocused) onDone()
                    focused = it.isFocused
                }
                .testTag(EditorTags.TEXT_FIELD),
        )
    }
}

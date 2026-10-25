package org.pixeldroid.media_editor.photoEdit.collage

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.pixeldroid.media_editor.photoEdit.CollageGeometry
import org.pixeldroid.media_editor.photoEdit.CollageSplit
import org.pixeldroid.media_editor.photoEdit.LocalLandscapePhotoShare
import org.pixeldroid.media_editor.photoEdit.panel.ResettableSlider
import org.pixeldroid.media_editor.photoEdit.R
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress

/** Test tags, used by the UI tests */
object CollageTags {
    fun layout(layout: Int) = "collage_layout_$layout"
    fun area(area: Int) = "collage_area_$area"
    const val PICK_MANY = "collage_pick_many"
    fun number(area: Int) = "collage_number_$area"
    const val SAVE = "collage_save"
    const val SAVE_AS = "collage_save_as"
    const val MODE = "collage_mode"
    const val PREVIEW = "collage_preview"
    const val LINE_X = "collage_line_x"
    const val LINE_Y = "collage_line_y"
    const val MIDDLE = "collage_middle"
}

/** Description of each layout (see CollageGeometry.LAYOUTS) */
@StringRes
private fun layoutDescription(layout: Int): Int = when (layout) {
    2 -> R.string.collage_layout_side_by_side
    3 -> R.string.collage_layout_stacked
    4 -> R.string.collage_layout_grid
    else -> R.string.collage_layout_grid_3
}

/**
 * The collage flow: choose a layout, then the images. Connects the [CollageViewModel] with the
 * image picker and the "save as" dialog.
 */
@Composable
fun CollageApp(viewModel: CollageViewModel, onSaved: (Uri) -> Unit, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Configuration-aware (language, …), unlike context.resources
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // The collage on its own, without the layouts: opened once photos are picked
    var collageOpen by rememberSaveable { mutableStateOf(false) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.imagePicked(uri)
        if (uri != null) collageOpen = true
    }
    // Several at once, as many as there are free areas (Android's picker needs at least 2)
    val pickLimit = viewModel.pickLimit()
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxOf(2, pickLimit))) { uris ->
        viewModel.imagesPicked(uris.take(pickLimit))
        if (uris.isNotEmpty()) collageOpen = true
    }
    val saveAs = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(viewModel.directSave.format.mimeType)
    ) { uri -> viewModel.saveAsChosen(uri) }

    // One-time actions requested through the state
    LaunchedEffect(state.saveAsName) {
        val name = state.saveAsName ?: return@LaunchedEffect
        viewModel.saveAsShown()
        saveAs.launch(name)
    }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        viewModel.messageShown()
        // Own scope: clearing the message must not cancel showing it
        scope.launch { snackbarHostState.showSnackbar(resources.getString(message)) }
    }
    LaunchedEffect(state.result) {
        state.result?.let(onSaved)
    }

    val layout = state.layout ?: CollageGeometry.LAYOUTS.first()
    // Back from the collage: to the layouts, the photos kept
    BackHandler(enabled = collageOpen) { collageOpen = false }
    run {
        ImagesContent(
            layout = layout,
            chooseLayout = !collageOpen,
            preview = state.preview,
            canSave = state.canSave,
            saving = state.saving,
            showSaveAs = viewModel.directSave.isAvailable,
            snackbarHostState = snackbarHostState,
            onAreaClick = { area ->
                viewModel.pickImageFor(area)
                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            filledAreas = state.images.keys,
            onPickMany = {
                if (pickLimit > 1) {
                    // Numbered in the order they are tapped, the order they fill the areas
                    pickImages.launch(
                        PickVisualMediaRequest.Builder()
                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            .setOrderedSelection(true)
                            .build()
                    )
                } else {
                    // One area left: the single picker, for that area
                    val area = CollageImages.freeAreas(state.images, CollageGeometry.imageCount(layout), CollageGeometry.fillOrder(layout)).firstOrNull() ?: 1
                    viewModel.pickImageFor(area)
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            },
            onMove = viewModel::moveImage,
            onCycleMode = viewModel::cycleMode,
            onSave = viewModel::save,
            onSaveAs = viewModel::saveAs,
            onBack = { if (collageOpen) collageOpen = false else onClose() },
            onChooseLayout = { chosen ->
                viewModel.chooseLayout(chosen)
                // Straight to the collage: photos are picked there, the ones already picked kept
                collageOpen = true
            },
            split = state.split,
            onSplitChange = viewModel::setSplit,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollageTopBar(
    @StringRes title: Int,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = { Text(stringResource(title)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                )
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    )
}


/** Second screen: tap an area to choose its image; the preview is the collage as it will be saved */
@Composable
fun ImagesContent(
    layout: Int,
    preview: Bitmap?,
    canSave: Boolean,
    saving: Boolean,
    showSaveAs: Boolean,
    snackbarHostState: SnackbarHostState,
    onAreaClick: (Int) -> Unit,
    onCycleMode: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** The layouts to choose from and the button to pick photos; false: the collage alone */
    chooseLayout: Boolean = true,
    /** Areas that have an image: only those can be dragged */
    filledAreas: Set<Int> = emptySet(),
    onPickMany: () -> Unit = {},
    onMove: (from: Int, to: Int) -> Unit = { _, _ -> },
    onChooseLayout: (Int) -> Unit = {},
    /** Where the lines between the areas are, set with sliders on the collage alone */
    split: CollageSplit = CollageSplit(),
    onSplitChange: (CollageSplit) -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            CollageTopBar(R.string.collage, onBack) {
                IconButton(onClick = onCycleMode, modifier = Modifier.testTag(CollageTags.MODE)) {
                    Icon(painterResource(R.drawable.ic_autorenew_24dp), stringResource(R.string.collage_mode_cycle))
                }
                IconButton(onClick = onSave, enabled = canSave, modifier = Modifier.testTag(CollageTags.SAVE)) {
                    Icon(painterResource(R.drawable.ic_save_24dp), stringResource(R.string.save_edit_menu))
                }
                if (showSaveAs) {
                    IconButton(onClick = onSaveAs, enabled = canSave, modifier = Modifier.testTag(CollageTags.SAVE_AS)) {
                        Icon(painterResource(R.drawable.ic_save_as_24dp), stringResource(R.string.save_as))
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        // The layouts, each as a small sketch with its numbers
        @Composable
        fun LayoutChoice() = Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            CollageGeometry.LAYOUTS.forEach { option ->
                LayoutSketch(option, selected = option == layout, onClick = { onChooseLayout(option) }, modifier = Modifier.weight(1f))
            }
        }

        @Composable
        fun PickMany(modifier: Modifier = Modifier) = FilledTonalButton(onClick = onPickMany, modifier = modifier.testTag(CollageTags.PICK_MANY)) {
            Text(stringResource(R.string.collage_pick_images))
        }

        // The collage as large as fits: in landscape its side is the height, not the width
        val canvasSize = Modifier.aspectRatio(1f, matchHeightConstraintsFirst = true)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            if (saving) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 16.dp))
            BoxWithConstraints(Modifier.fillMaxSize()) {
                if (!chooseLayout && maxWidth > maxHeight) {
                    // The collage alone in landscape: on the left as high as the screen (at most the
                    // share from the settings), the sliders and the button on the right
                    val side = minOf(maxWidth * LocalLandscapePhotoShare.current, maxHeight)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
                        Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.width(side).fillMaxHeight()) {
                            CollageCanvas(layout, preview, filledAreas, onAreaClick, onMove, canvasSize, split)
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        ) {
                            SplitSliders(layout, split, onSplitChange)
                            PickMany(Modifier.padding(top = 16.dp))
                        }
                    }
                } else if (!chooseLayout) {
                    // The collage alone, as large as fits, with the sliders for its lines below
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        CollageCanvas(layout, preview, filledAreas, onAreaClick, onMove, Modifier.weight(1f, fill = false).then(canvasSize), split)
                        SplitSliders(layout, split, onSplitChange)
                        PickMany(Modifier.padding(top = 16.dp))
                    }
                } else if (maxWidth > maxHeight) {
                    // Landscape: the layouts on the left, the collage on the right
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            LayoutChoice()
                        }
                        CollageCanvas(layout, preview, filledAreas, onAreaClick, onMove, canvasSize.fillMaxHeight(), split)
                    }
                } else {
                    // Portrait: the layouts, the chosen one below, large
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
                        LayoutChoice()
                        CollageCanvas(layout, preview, filledAreas, onAreaClick, onMove, Modifier.padding(top = 16.dp).weight(1f, fill = false).then(canvasSize), split)
                    }
                }
            }
        }
    }
}

/** Square collage: template with the empty areas, the rendered preview, and one tap target per area */
@Composable
private fun CollageCanvas(
    layout: Int,
    preview: Bitmap?,
    filledAreas: Set<Int>,
    onAreaClick: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    /** Its size: square */
    modifier: Modifier,
    split: CollageSplit,
) {
    // The area being dragged (long press on an image), and where the finger is (canvas pixels)
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragAt by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(
        modifier.border(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        AreaOutlines(layout, Modifier.fillMaxSize(), split)
        if (preview != null) {
            Image(
                preview.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize().testTag(CollageTags.PREVIEW),
            )
        }
        // Tap targets from the same geometry the collage is drawn with. Later areas are on top
        // (the small image of layout 1 lies on the big one), like when drawing.
        val sizePx = with(LocalDensity.current) { maxWidth.roundToPx() }
        val density = LocalDensity.current
        val areas = 1..CollageGeometry.imageCount(layout)
        val cells = areas.associateWith { CollageGeometry.cell(layout, it, sizePx, sizePx, split) }
        // The area under the finger: the topmost one, like the tap targets
        fun areaAt(at: Offset): Int? = areas.reversed().firstOrNull { a ->
            val c = cells.getValue(a)
            at.x >= c.left && at.x < c.left + c.width && at.y >= c.top && at.y < c.top + c.height
        }
        val target = dragFrom?.let { areaAt(dragAt) }
        // Numbers: the order photos picked together fill the areas (top left, clockwise)
        val order = CollageGeometry.fillOrder(layout)
        val badge = with(density) { 12.dp.toPx() }
        for (area in areas) {
            val cell = cells.getValue(area)
            val description = stringResource(R.string.collage_choose_image, area) +
                (if (area in filledAreas) ". " + stringResource(R.string.collage_move_image) else "")
            val highlight = when {
                area == dragFrom -> Modifier.border(3.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                area == target -> Modifier.border(4.dp, MaterialTheme.colorScheme.primary)
                else -> Modifier
            }
            Box(
                Modifier
                    .offset(with(density) { cell.left.toDp() }, with(density) { cell.top.toDp() })
                    .size(with(density) { cell.width.toDp() }, with(density) { cell.height.toDp() })
                    .then(highlight)
                    .pointerInput(area, area in filledAreas) {
                        if (area !in filledAreas) return@pointerInput
                        // Long press, then drag onto another area: swapped or moved there
                        detectDragGesturesAfterLongPress(
                            onDragStart = { start ->
                                dragFrom = area
                                dragAt = Offset(cell.left + start.x, cell.top + start.y)
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragAt += amount
                            },
                            onDragEnd = {
                                val from = dragFrom
                                val to = areaAt(dragAt)
                                dragFrom = null
                                if (from != null && to != null) onMove(from, to)
                            },
                            onDragCancel = { dragFrom = null },
                        )
                    }
                    .clickable { onAreaClick(area) }
                    .semantics { contentDescription = description }
                    .testTag(CollageTags.area(area))
            ) {
                // In the top left corner, unless an area on top covers it there: then bottom right
                val corner = Offset(cell.left + badge, cell.top + badge)
                val covered = areas.any { other ->
                    other > area && cells.getValue(other).let { c ->
                        corner.x >= c.left && corner.x < c.left + c.width && corner.y >= c.top && corner.y < c.top + c.height
                    }
                }
                Text(
                    "${order.indexOf(area) + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(if (covered) Alignment.BottomEnd else Alignment.TopStart)
                        .padding(6.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), CircleShape)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                        .testTag(CollageTags.number(area)),
                )
            }
        }
    }
}

/**
 * Sliders for the lines of [layout]: side by side the vertical line, one above the other the
 * horizontal one, the 2 x 2 grid both, the 3 x 3 grid the size of its middle. A double tap on
 * a handle sets it back.
 */
@Composable
private fun SplitSliders(layout: Int, split: CollageSplit, onChange: (CollageSplit) -> Unit) {
    val line = CollageSplit.LINE_RANGE.start.toFloat()..CollageSplit.LINE_RANGE.endInclusive.toFloat()
    val initial = CollageSplit()
    @Composable
    fun SplitSlider(@StringRes label: Int, value: Double, range: ClosedFloatingPointRange<Float>, reset: Double, tag: String, set: (Double) -> CollageSplit) {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
        ResettableSlider(
            value = value.toFloat(),
            onChange = { onChange(set(it.toDouble())) },
            range = range,
            resetValue = reset.toFloat(),
            modifier = Modifier.fillMaxWidth().testTag(tag),
        )
    }
    when (layout) {
        2 -> SplitSlider(R.string.collage_line, split.x, line, initial.x, CollageTags.LINE_X) { split.copy(x = it) }
        3 -> SplitSlider(R.string.collage_line, split.y, line, initial.y, CollageTags.LINE_Y) { split.copy(y = it) }
        4 -> {
            SplitSlider(R.string.collage_line_vertical, split.x, line, initial.x, CollageTags.LINE_X) { split.copy(x = it) }
            SplitSlider(R.string.collage_line_horizontal, split.y, line, initial.y, CollageTags.LINE_Y) { split.copy(y = it) }
        }
        5 -> SplitSlider(
            R.string.collage_middle_size, split.middle,
            CollageGeometry.GRID_MIDDLE_MIN.toFloat()..CollageGeometry.GRID_MIDDLE_MAX.toFloat(),
            initial.middle, CollageTags.MIDDLE,
        ) { split.copy(middle = it) }
    }
}

/** The areas of [layout] as outlines (where no photo is yet) */
@Composable
private fun AreaOutlines(layout: Int, modifier: Modifier = Modifier, split: CollageSplit = CollageSplit()) {
    val line = MaterialTheme.colorScheme.outline
    val fill = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier) {
        val w = size.width.toInt()
        val h = size.height.toInt()
        for (area in 1..CollageGeometry.imageCount(layout)) {
            val c = CollageGeometry.cell(layout, area, w, h, split)
            val topLeft = Offset(c.left.toFloat(), c.top.toFloat())
            val areaSize = Size(c.width.toFloat(), c.height.toFloat())
            drawRect(fill, topLeft, areaSize)
            drawRect(line, topLeft, areaSize, style = Stroke(width = 1.dp.toPx()))
        }
    }
}

/** A layout to choose: its areas as a sketch, each with its number in the fill order */
@Composable
private fun LayoutSketch(layout: Int, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(layoutDescription(layout))
    val order = CollageGeometry.fillOrder(layout)
    val numberColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val numberStyle = MaterialTheme.typography.labelSmall.copy(color = numberColor)
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .aspectRatio(1f)
            .clip(shape)
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(4.dp)
            .semantics { contentDescription = description }
            .testTag(CollageTags.layout(layout)),
    ) {
        AreaOutlines(layout, Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width.toInt()
            val h = size.height.toInt()
            for (area in 1..CollageGeometry.imageCount(layout)) {
                val c = CollageGeometry.cell(layout, area, w, h)
                val text = measurer.measure("${order.indexOf(area) + 1}", numberStyle)
                drawText(
                    text,
                    topLeft = Offset(
                        c.left + (c.width - text.size.width) / 2f,
                        c.top + (c.height - text.size.height) / 2f,
                    ),
                )
            }
        }
    }
}

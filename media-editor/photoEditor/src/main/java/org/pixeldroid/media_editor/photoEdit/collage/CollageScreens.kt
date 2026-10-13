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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import org.pixeldroid.media_editor.photoEdit.R

/** Test tags, used by the UI tests */
object CollageTags {
    fun layout(layout: Int) = "collage_layout_$layout"
    fun area(area: Int) = "collage_area_$area"
    const val SAVE = "collage_save"
    const val SAVE_AS = "collage_save_as"
    const val MODE = "collage_mode"
    const val PREVIEW = "collage_preview"
}

/** Template image and description of each layout (see CollageGeometry) */
private data class LayoutInfo(val layout: Int, @DrawableRes val template: Int, @StringRes val description: Int)

private val layouts = listOf(
    LayoutInfo(1, R.drawable.bitmap, R.string.collage_layout_inset),
    LayoutInfo(2, R.drawable.split_middle_vertical_collage, R.string.collage_layout_side_by_side),
    LayoutInfo(3, R.drawable.split_middle_horizontal_collage, R.string.collage_layout_stacked),
    LayoutInfo(4, R.drawable.split_4_collage, R.string.collage_layout_grid),
)

/**
 * The collage flow: choose a layout, then the images. Connects the [CollageViewModel] with the
 * image picker and the "save as" dialog.
 */
@Composable
fun CollageApp(viewModel: CollageViewModel, onSaved: (Uri) -> Unit, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.imagePicked(uri)
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
        scope.launch { snackbarHostState.showSnackbar(context.getString(message)) }
    }
    LaunchedEffect(state.result) {
        state.result?.let(onSaved)
    }

    val layout = state.layout
    if (layout == null) {
        LayoutChoiceContent(onChoose = viewModel::chooseLayout, onBack = onClose)
    } else {
        BackHandler(onBack = viewModel::backToLayouts)
        ImagesContent(
            layout = layout,
            preview = state.preview,
            canSave = state.canSave,
            saving = state.saving,
            showSaveAs = viewModel.directSave.isAvailable,
            snackbarHostState = snackbarHostState,
            onAreaClick = { area ->
                viewModel.pickImageFor(area)
                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onCycleMode = viewModel::cycleMode,
            onSave = viewModel::save,
            onSaveAs = viewModel::saveAs,
            onBack = viewModel::backToLayouts,
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

/** First screen: the four layouts as a 2 x 2 grid */
@Composable
fun LayoutChoiceContent(onChoose: (Int) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = { CollageTopBar(R.string.choose_collage_format, onBack) },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            layouts.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { info ->
                        Image(
                            painter = painterResource(info.template),
                            contentDescription = stringResource(info.description),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clickable { onChoose(info.layout) }
                                .testTag(CollageTags.layout(info.layout)),
                        )
                    }
                }
            }
        }
    }
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
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            CollageTopBar(R.string.choose_the_images, onBack) {
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            if (saving) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 16.dp))
            CollageCanvas(layout, preview, onAreaClick)
        }
    }
}

/** Square collage: template with the empty areas, the rendered preview, and one tap target per area */
@Composable
private fun CollageCanvas(layout: Int, preview: Bitmap?, onAreaClick: (Int) -> Unit) {
    val template = layouts.first { it.layout == layout }.template
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .border(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Image(painterResource(template), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
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
        for (area in 1..CollageGeometry.imageCount(layout)) {
            val cell = CollageGeometry.cell(layout, area, sizePx, sizePx)
            val description = stringResource(R.string.collage_choose_image, area)
            Box(
                Modifier
                    .offset(with(density) { cell.left.toDp() }, with(density) { cell.top.toDp() })
                    .size(with(density) { cell.width.toDp() }, with(density) { cell.height.toDp() })
                    .clickable { onAreaClick(area) }
                    .semantics { contentDescription = description }
                    .testTag(CollageTags.area(area))
            )
        }
    }
}

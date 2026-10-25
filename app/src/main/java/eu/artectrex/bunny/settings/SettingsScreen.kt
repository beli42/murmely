package eu.artectrex.bunny.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.artectrex.bunny.R
import eu.artectrex.bunny.SaveSettings
import org.pixeldroid.media_editor.common.ui.BunnyTheme
import org.pixeldroid.media_editor.common.LANDSCAPE_PHOTO_STEPS
import org.pixeldroid.media_editor.common.CLICK_ZOOM_STEPS
import kotlin.math.roundToInt

/** Test tags, used by the UI tests */
object SettingsTags {
    const val LOCATION = "settings_location"
    const val PICK_FOLDER = "settings_pick_folder"
    const val FORMAT_PNG = "settings_format_png"
    const val FORMAT_JPEG = "settings_format_jpeg"
    const val JPEG_QUALITY = "settings_jpeg_quality"
    const val LANDSCAPE_PHOTO = "settings_landscape_photo"
    const val CLICK_ZOOM = "settings_click_zoom"
}

/** Settings screen connected to its ViewModel and the system folder picker */
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::onFolderChosen)
    }
    SettingsContent(
        state = state,
        onBack = onBack,
        onPickFolder = { pickFolder.launch(SaveSettings.pickerStartUri(context)) },
        onFormatChange = viewModel::onFormatChange,
        onQualityChange = viewModel::onQualityChange,
        onQualityChangeFinished = viewModel::onQualityChangeFinished,
        onLandscapePhotoChange = viewModel::onLandscapePhotoChange,
        onLandscapePhotoChangeFinished = viewModel::onLandscapePhotoChangeFinished,
        onClickZoomChange = viewModel::onClickZoomChange,
        onClickZoomChangeFinished = viewModel::onClickZoomChangeFinished,
    )
}

/** The screen itself: only shows [state] and reports actions (no logic, easy to preview and test) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    state: SettingsUiState,
    onBack: () -> Unit,
    onPickFolder: () -> Unit,
    onFormatChange: (String) -> Unit,
    onQualityChange: (Int) -> Unit,
    onQualityChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    onLandscapePhotoChange: (Int) -> Unit = {},
    onLandscapePhotoChangeFinished: () -> Unit = {},
    onClickZoomChange: (Int) -> Unit = {},
    onClickZoomChangeFinished: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SectionHeader(stringResource(R.string.settings_category_saving))

            ListItem(
                headlineContent = { Text(stringResource(R.string.save_location_current)) },
                supportingContent = { Text(state.location) },
                modifier = Modifier.testTag(SettingsTags.LOCATION),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.save_folder_pick)) },
                supportingContent = { Text(stringResource(R.string.save_folder_pick_summary)) },
                modifier = Modifier
                    .clickable(onClick = onPickFolder)
                    .testTag(SettingsTags.PICK_FOLDER),
            )

            HorizontalDivider()
            SectionHeader(stringResource(R.string.save_format))

            Column(Modifier.selectableGroup()) {
                FormatOption(
                    label = stringResource(R.string.save_format_png),
                    selected = !state.isJpeg,
                    onSelect = { onFormatChange(SettingsUiState.FORMAT_PNG) },
                    tag = SettingsTags.FORMAT_PNG,
                )
                FormatOption(
                    label = stringResource(R.string.save_format_jpeg),
                    selected = state.isJpeg,
                    onSelect = { onFormatChange(SettingsUiState.FORMAT_JPEG) },
                    tag = SettingsTags.FORMAT_JPEG,
                )
            }

            // The quality only matters for JPG
            if (state.isJpeg) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.save_jpeg_quality)) },
                    supportingContent = { Text(stringResource(R.string.save_jpeg_quality_summary)) },
                    trailingContent = { Text(state.jpegQuality.toString(), style = MaterialTheme.typography.titleMedium) },
                )
                Slider(
                    value = state.jpegQuality.toFloat(),
                    onValueChange = { onQualityChange(it.roundToInt()) },
                    onValueChangeFinished = onQualityChangeFinished,
                    valueRange = 10f..100f,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .testTag(SettingsTags.JPEG_QUALITY),
                )
            }

            HorizontalDivider()
            SectionHeader(stringResource(R.string.settings_category_display))
            // Five sizes, from smaller to bigger (the nearest one to what is stored)
            val steps = LANDSCAPE_PHOTO_STEPS
            val step = steps.indices.minBy { kotlin.math.abs(steps[it] - state.landscapePhotoPercent) }
            ListItem(
                headlineContent = { Text(stringResource(R.string.landscape_photo)) },
                supportingContent = { Text(stringResource(R.string.landscape_photo_summary)) },
                trailingContent = { Text(stringResource(LANDSCAPE_PHOTO_NAMES[step]), style = MaterialTheme.typography.titleMedium) },
            )
            Slider(
                value = step.toFloat(),
                onValueChange = { onLandscapePhotoChange(steps[it.roundToInt().coerceIn(steps.indices)]) },
                onValueChangeFinished = onLandscapePhotoChangeFinished,
                valueRange = 0f..steps.lastIndex.toFloat(),
                steps = steps.size - 2,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .testTag(SettingsTags.LANDSCAPE_PHOTO),
            )

            // Click zoom: off, or after one of a few times
            val times = CLICK_ZOOM_STEPS
            val time = times.indices.minBy { kotlin.math.abs(times[it] - state.clickZoomMs) }
            ListItem(
                headlineContent = { Text(stringResource(R.string.click_zoom)) },
                supportingContent = { Text(stringResource(R.string.click_zoom_summary)) },
                trailingContent = {
                    Text(
                        if (times[time] == 0) stringResource(R.string.click_zoom_off) else stringResource(R.string.click_zoom_seconds, times[time] / 1000),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
            )
            Slider(
                value = time.toFloat(),
                onValueChange = { onClickZoomChange(times[it.roundToInt().coerceIn(times.indices)]) },
                onValueChangeFinished = onClickZoomChangeFinished,
                valueRange = 0f..times.lastIndex.toFloat(),
                steps = times.size - 2,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .testTag(SettingsTags.CLICK_ZOOM),
            )
        }
    }
}

/** Names of [LANDSCAPE_PHOTO_STEPS] */
private val LANDSCAPE_PHOTO_NAMES = listOf(
    R.string.landscape_photo_smaller, R.string.landscape_photo_small, R.string.landscape_photo_normal,
    R.string.landscape_photo_big, R.string.landscape_photo_bigger,
)

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun FormatOption(label: String, selected: Boolean, onSelect: () -> Unit, tag: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(tag),
    ) {
        // onClick = null: the whole row is the click target (accessibility)
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 16.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsPreview() {
    BunnyTheme {
        SettingsContent(
            state = SettingsUiState("Pictures/Murmely", SettingsUiState.FORMAT_JPEG, 93),
            onBack = {}, onPickFolder = {}, onFormatChange = {}, onQualityChange = {}, onQualityChangeFinished = {},
        )
    }
}

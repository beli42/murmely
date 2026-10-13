package eu.artectrex.bunny.start

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.artectrex.bunny.R
import eu.artectrex.bunny.VideoPhase
import eu.artectrex.bunny.VideoProgress
import org.pixeldroid.media_editor.common.ui.BunnyTheme

/** Test tags, used by the UI tests */
object StartTags {
    const val EDIT_TILE = "start_edit_tile"
    const val COLLAGE_TILE = "start_collage_tile"
    const val EXIT = "start_exit"
    const val MORE = "start_more"
    const val SETTINGS = "start_menu_settings"
    const val ABOUT = "start_menu_about"
    const val EXIT_MENU = "start_menu_exit"
    const val VIDEO_PROGRESS = "video_progress"
    const val CANCEL_VIDEO = "video_cancel"
}

/** Start screen: two large tiles, exit icon and three-dot menu */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartContent(
    snackbarHostState: SnackbarHostState,
    onEdit: () -> Unit,
    onCollage: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.choose_file_fragment_label)) },
                actions = {
                    // Exit as an icon (door with arrow) ...
                    IconButton(onClick = onExit, modifier = Modifier.testTag(StartTags.EXIT)) {
                        Icon(painterResource(R.drawable.ic_exit), contentDescription = stringResource(R.string.exit))
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag(StartTags.MORE)) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            MenuEntry(R.string.settings, StartTags.SETTINGS) { menuOpen = false; onSettings() }
                            MenuEntry(R.string.about, StartTags.ABOUT) { menuOpen = false; onAbout() }
                            // ... and as a menu entry
                            MenuEntry(R.string.exit, StartTags.EXIT_MENU) { menuOpen = false; onExit() }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Tiles side by side in landscape, on top of each other in portrait
            if (maxWidth > maxHeight) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    EditTile(onEdit, Modifier.weight(1f).fillMaxHeight())
                    CollageTile(onCollage, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    EditTile(onEdit, Modifier.weight(1f).fillMaxWidth())
                    CollageTile(onCollage, Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun MenuEntry(@StringRes text: Int, tag: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(text)) }, onClick = onClick, modifier = Modifier.testTag(tag))
}

@Composable
private fun EditTile(onClick: () -> Unit, modifier: Modifier) = StartTile(
    icon = R.drawable.ic_photo_library,
    title = stringResource(R.string.file_chooser),
    description = stringResource(R.string.file_chooser_description),
    onClick = onClick,
    modifier = modifier.testTag(StartTags.EDIT_TILE),
)

@Composable
private fun CollageTile(onClick: () -> Unit, modifier: Modifier) = StartTile(
    icon = R.drawable.ic_collage,
    title = stringResource(R.string.make_collage),
    description = stringResource(R.string.make_collage_description),
    onClick = onClick,
    modifier = modifier.testTag(StartTags.COLLAGE_TILE),
)

/** Large tile, the whole tile is clickable */
@Composable
private fun StartTile(
    @DrawableRes icon: Int,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Card(onClick = onClick, shape = RoundedCornerShape(28.dp), modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(96.dp),
            )
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Progress of converting an edited video */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoProgressContent(
    video: VideoProgress?,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.second_fragment_label)) },
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
        ) {
            Text(
                text = when (video?.phase) {
                    VideoPhase.ANALYSING -> stringResource(R.string.video_analysing)
                    VideoPhase.ENCODING -> stringResource(R.string.video_encoding, video.percent)
                    VideoPhase.FINISHED -> stringResource(R.string.video_finished)
                    VideoPhase.FAILED -> stringResource(R.string.video_failed)
                    null -> ""
                },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            if (video != null && video.phase.isRunning) {
                LinearProgressIndicator(
                    progress = { video.percent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp)
                        .testTag(StartTags.VIDEO_PROGRESS),
                )
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .testTag(StartTags.CANCEL_VIDEO),
                ) {
                    Text(stringResource(R.string.cancel_video_processing))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StartPreview() {
    BunnyTheme {
        StartContent(remember { SnackbarHostState() }, {}, {}, {}, {}, {})
    }
}

@Preview(showBackground = true)
@Composable
private fun VideoProgressPreview() {
    BunnyTheme {
        VideoProgressContent(VideoProgress(VideoPhase.ENCODING, 42), {}, {})
    }
}

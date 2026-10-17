package org.pixeldroid.media_editor.photoEdit.panel

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation
import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.frame.FrameGeometry
import org.pixeldroid.media_editor.photoEdit.frame.FramePlacement
import org.pixeldroid.media_editor.photoEdit.frame.FrameRenderer
import org.pixeldroid.media_editor.photoEdit.frame.FrameSettings
import org.pixeldroid.media_editor.photoEdit.frame.FrameStyle
import org.pixeldroid.media_editor.photoEdit.overlay.ToolStyle
import org.pixeldroid.media_editor.photoEdit.overlay.hslColor

/** Name of each frame style */
@get:StringRes
val FrameStyle.label: Int
    get() = when (this) {
        FrameStyle.SOLID -> R.string.frame_solid
        FrameStyle.DOUBLE_LINE -> R.string.frame_double_line
        FrameStyle.POLAROID -> R.string.frame_polaroid
        FrameStyle.PASSEPARTOUT -> R.string.frame_passepartout
        FrameStyle.ROUNDED -> R.string.frame_rounded
        FrameStyle.FILM -> R.string.frame_film
    }

/**
 * Frames: templates (with a small preview each, and "none"); with a frame chosen, inside or
 * outside, its width and colour. [onChoose] is a step in the history, [onAdjust] (sliders) is
 * recorded once they stop.
 */
@Composable
fun FramePanel(
    frame: FrameSettings?,
    onChoose: (FrameSettings?) -> Unit,
    onAdjust: (FrameSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            FrameTile(null, frame, stringResource(R.string.frame_none), PanelTags.FRAME_NONE) { onChoose(null) }
            FrameStyle.entries.forEach { style ->
                FrameTile(style, frame, stringResource(style.label), PanelTags.frameStyle(style)) {
                    // Another template keeps placement, width and colour
                    onChoose(frame?.copy(style = style) ?: FrameSettings.of(style))
                }
            }
        }
        if (frame == null) {
            Text(
                stringResource(R.string.hint_frame),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            return@Column
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = frame.placement == FramePlacement.INSIDE,
                    onClick = { onChoose(frame.copy(placement = FramePlacement.INSIDE)) },
                    label = { Text(stringResource(R.string.frame_inside)) },
                    modifier = Modifier.testTag(PanelTags.FRAME_INSIDE),
                )
                FilterChip(
                    selected = frame.placement == FramePlacement.OUTSIDE,
                    onClick = { onChoose(frame.copy(placement = FramePlacement.OUTSIDE)) },
                    label = { Text(stringResource(R.string.frame_outside)) },
                    modifier = Modifier.testTag(PanelTags.FRAME_OUTSIDE),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.frame_width),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(104.dp),
                )
                Slider(
                    value = frame.size,
                    onValueChange = { onAdjust(frame.copy(size = it)) },
                    valueRange = FrameSettings.SIZE_RANGE,
                    modifier = Modifier.weight(1f).testTag(PanelTags.FRAME_WIDTH),
                )
            }
            GradientSlider(
                label = stringResource(R.string.tool_hue),
                value = frame.hue,
                range = ToolStyle.HUE_RANGE,
                colors = (0..6).map { Color(hslColor(it * 60f, 1f, 0.5f)) },
                onChange = { onAdjust(frame.copy(hue = it)) },
                tag = PanelTags.FRAME_HUE,
            )
            GradientSlider(
                label = stringResource(R.string.tool_lightness),
                value = frame.lightness,
                range = ToolStyle.LIGHTNESS_RANGE,
                colors = listOf(Color.Black, Color(hslColor(frame.hue, 1f, 0.5f)), Color.White),
                onChange = { onAdjust(frame.copy(lightness = it)) },
                tag = PanelTags.FRAME_LIGHTNESS,
            )
        }
    }
}

/** A template: a small photo with that frame (none: the photo alone), its name below */
@Composable
private fun FrameTile(style: FrameStyle?, current: FrameSettings?, label: String, tag: String, onClick: () -> Unit) {
    val selected = current?.style == style
    // The template in the current colour; outside, so the whole frame shows
    val preview = style?.let {
        (current?.copy(style = it, placement = FramePlacement.OUTSIDE, size = PREVIEW_SIZE)
            ?: FrameSettings.of(it, size = PREVIEW_SIZE))
    }
    val shape = RoundedCornerShape(8.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clip(shape)
            .clickable(onClick = onClick)
            .testTag(tag),
    ) {
        val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        val sky = Color(0xFF8DB4D9).toArgb()
        val land = Color(0xFF6E9E5A).toArgb()
        Canvas(
            Modifier
                .padding(top = 4.dp)
                .size(64.dp, 48.dp)
                .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(4.dp))
                .padding(4.dp)
        ) {
            val (outWidth, outHeight) = FrameGeometry.outputSize(preview, PREVIEW_WIDTH, PREVIEW_HEIGHT)
            val scale = minOf(size.width / outWidth, size.height / outHeight)
            val left = (size.width - outWidth * scale) / 2
            val top = (size.height - outHeight * scale) / 2
            drawIntoCanvas { composeCanvas ->
                composeCanvas.nativeCanvas.withTranslation(left, top) { withScale(scale, scale) {
                    // A tiny landscape as the photo
                    val m = preview?.let { FrameGeometry.margins(it, PREVIEW_WIDTH, PREVIEW_HEIGHT) }
                    val x = (m?.left ?: 0).toFloat()
                    val y = (m?.top ?: 0).toFloat()
                    val paint = android.graphics.Paint()
                    paint.color = sky
                    drawRect(x, y, x + PREVIEW_WIDTH, y + PREVIEW_HEIGHT * 0.6f, paint)
                    paint.color = land
                    drawRect(x, y + PREVIEW_HEIGHT * 0.6f, x + PREVIEW_WIDTH, y + PREVIEW_HEIGHT, paint)
                    preview?.let { FrameRenderer.draw(this, it, PREVIEW_WIDTH, PREVIEW_HEIGHT) }
                } }
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
}

private const val PREVIEW_WIDTH = 400
private const val PREVIEW_HEIGHT = 300
private const val PREVIEW_SIZE = 0.1f

package org.pixeldroid.media_editor.photoEdit.panel

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.canvas.CanvasExtension
import org.pixeldroid.media_editor.photoEdit.canvas.Side
import org.pixeldroid.media_editor.photoEdit.overlay.ToolStyle
import org.pixeldroid.media_editor.photoEdit.overlay.hslColor
import kotlin.math.min
import kotlin.math.roundToInt

@get:StringRes
private val Side.label: Int
    get() = when (this) {
        Side.TOP -> R.string.side_top
        Side.BOTTOM -> R.string.side_bottom
        Side.LEFT -> R.string.side_left
        Side.RIGHT -> R.string.side_right
    }

/**
 * Making the canvas larger: the sides (each a toggle), the width added on them (with the
 * pixels for this photo) and the colour. A caption goes there with the text tool.
 */
@Composable
fun CanvasPanel(
    canvas: CanvasExtension?,
    photoSize: Pair<Int, Int>?,
    onChoose: (CanvasExtension?) -> Unit,
    onAdjust: (CanvasExtension) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Until it is used, all sides and white
    val current = canvas ?: CanvasExtension()
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Side.entries.forEach { side ->
                FilterChip(
                    selected = side in current.sides,
                    onClick = {
                        val sides = if (side in current.sides) current.sides - side else current.sides + side
                        onChoose(current.copy(sides = sides))
                    },
                    label = { Text(stringResource(side.label)) },
                    modifier = Modifier.testTag(PanelTags.canvasSide(side)),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.canvas_width),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(104.dp),
            )
            Slider(
                value = current.size,
                onValueChange = { onAdjust(current.copy(size = it)) },
                valueRange = CanvasExtension.SIZE_RANGE,
                enabled = current.sides.isNotEmpty(),
                modifier = Modifier.weight(1f).testTag(PanelTags.CANVAS_WIDTH),
            )
            // The pixels this adds to the photo on each chosen side
            val pixels = photoSize?.let { (w, h) -> (current.size * min(w, h)).roundToInt() }
            Text(
                pixels?.let { "$it px" } ?: "",
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                modifier = Modifier.width(64.dp),
            )
        }
        GradientSlider(
            label = stringResource(R.string.tool_hue),
            value = current.hue,
            range = ToolStyle.HUE_RANGE,
            colors = (0..6).map { Color(hslColor(it * 60f, 1f, 0.5f)) },
            onChange = { onAdjust(current.copy(hue = it)) },
            tag = PanelTags.CANVAS_HUE,
        )
        GradientSlider(
            label = stringResource(R.string.tool_lightness),
            value = current.lightness,
            range = ToolStyle.LIGHTNESS_RANGE,
            colors = listOf(Color.Black, Color(hslColor(current.hue, 1f, 0.5f)), Color.White),
            onChange = { onAdjust(current.copy(lightness = it)) },
            tag = PanelTags.CANVAS_LIGHTNESS,
        )
    }
}

package org.pixeldroid.media_editor.photoEdit.panel

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.pixeldroid.media_editor.photoEdit.Adjustment
import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.SliderValueFormat
import org.pixeldroid.media_editor.photoEdit.Sliders

/** Test tags, used by the UI tests */
object PanelTags {
    fun slider(adjustment: Adjustment) = "slider_${adjustment.name}"
    fun value(adjustment: Adjustment) = "slider_value_${adjustment.name}"
    const val UNDO = "history_undo"
    const val REDO = "history_redo"
    const val RESET = "history_reset"
    const val DRAW = "tool_draw"
    const val TEXT = "tool_text"
    const val STICKERS = "tool_stickers"
    const val CROP = "tool_crop"
}

/** Label of each adjustment */
@get:StringRes
val Adjustment.label: Int
    get() = when (this) {
        Adjustment.EXPOSURE -> R.string.lbl_exposure
        Adjustment.BRIGHTNESS -> R.string.lbl_brightness
        Adjustment.CONTRAST -> R.string.lbl_contrast
        Adjustment.HIGHLIGHTS -> R.string.lbl_highlights
        Adjustment.SHADOWS -> R.string.lbl_shadows
        Adjustment.CLARITY -> R.string.lbl_clarity
        Adjustment.SATURATION -> R.string.lbl_saturation
        Adjustment.VIBRANCE -> R.string.lbl_vibrance
        Adjustment.WARMTH -> R.string.lbl_warmth
        Adjustment.TINT -> R.string.lbl_tint
        Adjustment.SHARPNESS -> R.string.lbl_sharpness
        Adjustment.VIGNETTE -> R.string.lbl_vignette
        Adjustment.GRAIN -> R.string.lbl_grain
    }

/**
 * All adjustment sliders in a scrollable list. Each row: label, slider, value (-100..+100).
 * Double-tapping a slider or tapping its value resets it to 0.
 */
@Composable
fun AdjustmentPanel(sliders: Sliders, onChange: (Adjustment, Float) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp)
    ) {
        Adjustment.entries.forEach { adjustment ->
            AdjustmentRow(adjustment, sliders[adjustment]) { onChange(adjustment, it) }
        }
    }
}

@Composable
private fun AdjustmentRow(adjustment: Adjustment, value: Float, onChange: (Float) -> Unit) {
    val label = stringResource(adjustment.label)
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Fixed widths, so rows never change height or layout while values change
        Text(
            label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(104.dp),
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = adjustment.min..adjustment.max,
            modifier = Modifier
                .weight(1f)
                .onDoubleTap { onChange(0f) }
                .testTag(PanelTags.slider(adjustment)),
        )
        val reset = stringResource(R.string.reset_slider, label)
        Text(
            SliderValueFormat.label(SliderValueFormat.percent(value, adjustment.min, adjustment.max)),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            modifier = Modifier
                .width(52.dp)
                .clickable { onChange(0f) }
                .semantics { contentDescription = reset }
                .padding(4.dp)
                .testTag(PanelTags.value(adjustment)),
        )
    }
}

/**
 * Reports double taps without taking the touches away from the element (the slider still
 * moves normally). Observed in the initial pass, nothing is consumed.
 */
private fun Modifier.onDoubleTap(onDoubleTap: () -> Unit) = pointerInput(onDoubleTap) {
    var lastTapUp = 0L
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
        if (up == null) {
            lastTapUp = 0L
            return@awaitEachGesture
        }
        val isTap = up.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis &&
            (up.position - down.position).getDistance() < viewConfiguration.touchSlop
        if (!isTap) {
            lastTapUp = 0L
        } else if (lastTapUp != 0L && down.uptimeMillis - lastTapUp < viewConfiguration.doubleTapTimeoutMillis) {
            lastTapUp = 0L
            onDoubleTap()
        } else {
            lastTapUp = up.uptimeMillis
        }
    }
}

private data class Tool(@StringRes val label: Int, @DrawableRes val icon: Int, val tag: String, val onClick: () -> Unit)

/** Draw, text, stickers and crop */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolsPanel(
    onDraw: () -> Unit,
    onText: () -> Unit,
    onStickers: () -> Unit,
    onCrop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tools = listOf(
        Tool(R.string.draw, R.drawable.draw, PanelTags.DRAW, onDraw),
        Tool(R.string.add_text, R.drawable.text_fields, PanelTags.TEXT, onText),
        Tool(R.string.stickers, R.drawable.sticky_note, PanelTags.STICKERS, onStickers),
        Tool(R.string.crop, R.drawable.ic_crop_black_24dp, PanelTags.CROP, onCrop),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        tools.forEach { tool ->
            FilledTonalButton(onClick = tool.onClick, modifier = Modifier.testTag(tool.tag)) {
                Icon(painterResource(tool.icon), contentDescription = null)
                Text(stringResource(tool.label), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** Undo, redo and reset, shown on top of the photo */
@Composable
fun HistoryControls(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier.padding(8.dp)) {
        FilledTonalIconButton(onClick = onUndo, enabled = canUndo, modifier = Modifier.testTag(PanelTags.UNDO)) {
            Icon(painterResource(R.drawable.undo), stringResource(R.string.undo))
        }
        FilledTonalIconButton(onClick = onRedo, enabled = canRedo, modifier = Modifier.testTag(PanelTags.REDO)) {
            Icon(painterResource(R.drawable.redo), stringResource(R.string.redo))
        }
        // Reset undoes everything; it can be redone, so it is available as long as there is something to undo
        FilledTonalIconButton(onClick = onReset, enabled = canUndo, modifier = Modifier.testTag(PanelTags.RESET)) {
            Icon(painterResource(R.drawable.restore_24dp), stringResource(R.string.reset_edit_menu))
        }
    }
}

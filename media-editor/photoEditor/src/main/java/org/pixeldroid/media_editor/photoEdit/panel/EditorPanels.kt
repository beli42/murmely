package org.pixeldroid.media_editor.photoEdit.panel

import androidx.annotation.DrawableRes
import org.pixeldroid.media_editor.photoEdit.overlay.hslColor
import org.pixeldroid.media_editor.photoEdit.overlay.ToolStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.SliderDefaults
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import org.pixeldroid.media_editor.photoEdit.PhotoEditViewModel
import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.overlay.StickerCatalog
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
    const val HUE = "style_hue"
    const val LIGHTNESS = "style_lightness"
    const val SIZE = "style_size"
    const val STYLE_PREVIEW = "style_preview"
    const val STICKER_GALLERY = "sticker_gallery"
    fun sticker(code: String) = "sticker_$code"
    fun stickerCategory(category: StickerCatalog.Category) = "sticker_category_${category.name}"
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

/**
 * The "Edit" tab: draw, text and stickers as toggles (tap again to finish), crop, and below them
 * the settings or a hint for the active tool. Switching to another tab also finishes the tool,
 * so the sliders and filters can be used at any time in between.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolsPanel(
    activeTool: PhotoEditViewModel.ShownView,
    style: ToolStyle,
    onToggleTool: (PhotoEditViewModel.ShownView) -> Unit,
    onCrop: () -> Unit,
    onStyleChange: (ToolStyle) -> Unit,
    modifier: Modifier = Modifier,
    onAddSticker: (String) -> Unit = {},
    onStickerFromGallery: () -> Unit = {},
) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            ToolChip(R.string.draw, R.drawable.draw, PanelTags.DRAW, activeTool == PhotoEditViewModel.ShownView.Draw) {
                onToggleTool(PhotoEditViewModel.ShownView.Draw)
            }
            ToolChip(R.string.add_text, R.drawable.text_fields, PanelTags.TEXT, activeTool == PhotoEditViewModel.ShownView.Text) {
                onToggleTool(PhotoEditViewModel.ShownView.Text)
            }
            ToolChip(R.string.stickers, R.drawable.sticky_note, PanelTags.STICKERS, activeTool == PhotoEditViewModel.ShownView.Sticker) {
                onToggleTool(PhotoEditViewModel.ShownView.Sticker)
            }
            // Crop opens its own screen and comes back here
            ToolChip(R.string.crop, R.drawable.ic_crop_black_24dp, PanelTags.CROP, selected = false, onClick = onCrop)
        }
        when (activeTool) {
            PhotoEditViewModel.ShownView.Draw -> ToolSettingsPanel(style, forText = false, onChange = onStyleChange)
            PhotoEditViewModel.ShownView.Text -> {
                ToolSettingsPanel(style, forText = true, onChange = onStyleChange)
                ToolHint(R.string.hint_text)
            }
            PhotoEditViewModel.ShownView.Sticker -> {
                StickerPicker(onAdd = onAddSticker, onFromGallery = onStickerFromGallery)
                ToolHint(R.string.hint_sticker)
            }
            else -> Unit
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolChip(@StringRes label: Int, @DrawableRes icon: Int, tag: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(stringResource(label)) },
        leadingIcon = { Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp)) },
        modifier = Modifier.testTag(tag),
    )
}

@Composable
private fun ToolHint(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/**
 * Undo, redo and reset, shown on top of the photo. Always visible: on a translucent surface, and
 * disabled buttons are clearly greyed out instead of almost transparent (Material's default),
 * so they don't seem to appear only after the first edit.
 */
@Composable
fun HistoryControls(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = IconButtonDefaults.iconButtonColors(
        contentColor = MaterialTheme.colorScheme.onSurface,
        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    )
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
        modifier = modifier.padding(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(0.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
            IconButton(onClick = onUndo, enabled = canUndo, colors = colors, modifier = Modifier.testTag(PanelTags.UNDO)) {
                Icon(painterResource(R.drawable.undo), stringResource(R.string.undo))
            }
            IconButton(onClick = onRedo, enabled = canRedo, colors = colors, modifier = Modifier.testTag(PanelTags.REDO)) {
                Icon(painterResource(R.drawable.redo), stringResource(R.string.redo))
            }
            // Reset undoes everything; it can be redone, so it is available as long as there is something to undo
            IconButton(onClick = onReset, enabled = canUndo, colors = colors, modifier = Modifier.testTag(PanelTags.RESET)) {
                Icon(painterResource(R.drawable.restore_24dp), stringResource(R.string.reset_edit_menu))
            }
        }
    }
}

/**
 * Colour and size for drawing (line width) or text (text size), shown below the photo.
 * Colour = hue (rainbow) and lightness (black, the colour, white). A preview shows the result.
 */
@Composable
fun ToolSettingsPanel(
    style: ToolStyle,
    forText: Boolean,
    onChange: (ToolStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = Color(style.color)
    Column(modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        // Preview: a line of the chosen width, or "Aa" in the chosen size (scaled to a typical photo width)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag(PanelTags.STYLE_PREVIEW),
        ) {
            if (forText) {
                val size = (style.textSize * 360).coerceIn(10f, 48f)
                Text("Aa", color = color, fontSize = size.sp)
            } else {
                Canvas(Modifier.fillMaxWidth(0.6f).height(48.dp)) {
                    val stroke = (style.strokeWidth * 360.dp.toPx()).coerceAtLeast(1f)
                    drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), stroke, StrokeCap.Round)
                }
            }
        }
        GradientSlider(
            label = stringResource(R.string.tool_hue),
            value = style.hue,
            range = ToolStyle.HUE_RANGE,
            colors = (0..6).map { Color(hslColor(it * 60f, 1f, 0.5f)) },
            onChange = { onChange(style.copy(hue = it)) },
            tag = PanelTags.HUE,
        )
        GradientSlider(
            label = stringResource(R.string.tool_lightness),
            value = style.lightness,
            range = ToolStyle.LIGHTNESS_RANGE,
            colors = listOf(Color.Black, Color(hslColor(style.hue, 1f, 0.5f)), Color.White),
            onChange = { onChange(style.copy(lightness = it)) },
            tag = PanelTags.LIGHTNESS,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (forText) R.string.tool_text_size else R.string.tool_line_width),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(104.dp),
            )
            Slider(
                value = if (forText) style.textSize else style.strokeWidth,
                onValueChange = { onChange(if (forText) style.copy(textSize = it) else style.copy(strokeWidth = it)) },
                valueRange = if (forText) ToolStyle.TEXT_SIZE_RANGE else ToolStyle.STROKE_WIDTH_RANGE,
                modifier = Modifier.weight(1f).testTag(PanelTags.SIZE),
            )
        }
    }
}

/** A slider whose track shows a colour gradient */
@Composable
private fun GradientSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    colors: List<Color>,
    onChange: (Float) -> Unit,
    tag: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(104.dp),
        )
        Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .height(12.dp)
                    .background(Brush.horizontalGradient(colors), RoundedCornerShape(6.dp))
            )
            Slider(
                value = value,
                onValueChange = onChange,
                valueRange = range,
                colors = SliderDefaults.colors(
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().testTag(tag),
            )
        }
    }
}

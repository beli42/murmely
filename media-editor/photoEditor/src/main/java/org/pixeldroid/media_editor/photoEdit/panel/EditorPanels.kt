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
import org.pixeldroid.media_editor.photoEdit.EditorTags
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.gestures.detectTapGestures
import org.pixeldroid.media_editor.photoEdit.canvas.CanvasExtension
import org.pixeldroid.media_editor.photoEdit.canvas.Side
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.layout.fillMaxHeight
import org.pixeldroid.media_editor.photoEdit.overlay.TextEffects
import org.pixeldroid.media_editor.photoEdit.overlay.TextItem
import org.pixeldroid.media_editor.photoEdit.overlay.TextGeometry
import org.pixeldroid.media_editor.photoEdit.overlay.OverlayRenderer
import org.pixeldroid.media_editor.photoEdit.frame.FrameSettings
import org.pixeldroid.media_editor.photoEdit.frame.FrameStyle
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Typeface
import org.pixeldroid.media_editor.photoEdit.overlay.TextFont
import org.pixeldroid.media_editor.photoEdit.overlay.TextTypefaces
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.remember
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
    const val LAYER_BACK = "layer_back"
    const val HISTORY_HANDLE = "history_handle"
    const val EFFECTS = "text_effects"
    const val OUTLINE = "effect_outline"
    const val LETTER_SPACING = "effect_letter_spacing"
    const val SHADOW = "effect_shadow"
    const val DROP_SHADOW = "effect_drop_shadow"
    const val DROP_SHADOW_ANGLE = "effect_drop_shadow_angle"
    fun effectColor(effect: String, color: Int) = "${effect}_color_${Integer.toHexString(color)}"
    const val FRAME = "tool_frame"
    const val CANVAS = "tool_canvas"
    fun canvasSide(side: Side) = "canvas_side_${side.name}"
    const val CANVAS_WIDTH = "canvas_width"
    const val CANVAS_HUE = "canvas_hue"
    const val CANVAS_LIGHTNESS = "canvas_lightness"
    const val FRAME_NONE = "frame_none"
    fun frameStyle(style: FrameStyle) = "frame_${style.name}"
    const val FRAME_INSIDE = "frame_inside"
    const val FRAME_OUTSIDE = "frame_outside"
    const val FRAME_WIDTH = "frame_width"
    const val FRAME_HUE = "frame_hue"
    const val FRAME_LIGHTNESS = "frame_lightness"
    const val LAYER_FORWARD = "layer_forward"
    fun font(font: TextFont) = "font_${font.name}"
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
    layer: LayerState? = null,
    onLayer: (forward: Boolean) -> Unit = {},
    frame: FrameSettings? = null,
    onChooseFrame: (FrameSettings?) -> Unit = {},
    onAdjustFrame: (FrameSettings) -> Unit = {},
    canvas: CanvasExtension? = null,
    photoSize: Pair<Int, Int>? = null,
    onChooseCanvas: (CanvasExtension?) -> Unit = {},
    onAdjustCanvas: (CanvasExtension) -> Unit = {},
) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        // Wrapping onto a second line when the tools don't fit; every tool shows its whole name
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            // Crop opens its own screen and comes back here
            ToolChip(R.string.crop, R.drawable.ic_crop_black_24dp, PanelTags.CROP, selected = false, onClick = onCrop)
            ToolChip(R.string.draw, R.drawable.draw, PanelTags.DRAW, activeTool == PhotoEditViewModel.ShownView.Draw) {
                onToggleTool(PhotoEditViewModel.ShownView.Draw)
            }
            ToolChip(R.string.tool_text, R.drawable.text_fields, PanelTags.TEXT, activeTool == PhotoEditViewModel.ShownView.Text) {
                onToggleTool(PhotoEditViewModel.ShownView.Text)
            }
            ToolChip(R.string.tool_stickers, R.drawable.sticky_note, PanelTags.STICKERS, activeTool == PhotoEditViewModel.ShownView.Sticker) {
                onToggleTool(PhotoEditViewModel.ShownView.Sticker)
            }
            ToolChip(R.string.canvas, R.drawable.ic_canvas_24dp, PanelTags.CANVAS, activeTool == PhotoEditViewModel.ShownView.Canvas) {
                onToggleTool(PhotoEditViewModel.ShownView.Canvas)
            }
            ToolChip(R.string.frame, R.drawable.ic_frame_24dp, PanelTags.FRAME, activeTool == PhotoEditViewModel.ShownView.Frame) {
                onToggleTool(PhotoEditViewModel.ShownView.Frame)
            }
        }
        when (activeTool) {
            PhotoEditViewModel.ShownView.Draw -> ToolSettingsPanel(style, forText = false, onChange = onStyleChange)
            PhotoEditViewModel.ShownView.Text -> {
                // Layer buttons of the selected text right of the preview
                ToolSettingsPanel(
                    style, forText = true, onChange = onStyleChange,
                    previewEnd = layer?.let { state -> @Composable { LayerButtons(state, onLayer) } },
                )
            }
            PhotoEditViewModel.ShownView.Sticker -> {
                // Layer buttons of the selected sticker, on the right like for texts
                if (layer != null) {
                    Box(contentAlignment = Alignment.CenterEnd, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        LayerButtons(layer, onLayer)
                    }
                }
                StickerPicker(onAdd = onAddSticker, onFromGallery = onStickerFromGallery)
            }
            PhotoEditViewModel.ShownView.Frame -> FramePanel(frame, onChoose = onChooseFrame, onAdjust = onAdjustFrame)
            PhotoEditViewModel.ShownView.Canvas -> CanvasPanel(canvas, photoSize, onChoose = onChooseCanvas, onAdjust = onAdjustCanvas)
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
        label = { Text(stringResource(label), maxLines = 1, softWrap = false) },
        leadingIcon = { Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp)) },
        modifier = Modifier.testTag(tag),
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
    handleModifier: Modifier? = null,
    /** Reset resets the area shown (filter, sliders, the active tool); null: like undo */
    canReset: Boolean? = null,
    /** Pressed (true) and released (false): shows the original while held, if not null */
    onCompare: ((Boolean) -> Unit)? = null,
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            IconButton(onClick = onUndo, enabled = canUndo, colors = colors, modifier = Modifier.testTag(PanelTags.UNDO)) {
                Icon(painterResource(R.drawable.undo), stringResource(R.string.undo))
            }
            IconButton(onClick = onRedo, enabled = canRedo, colors = colors, modifier = Modifier.testTag(PanelTags.REDO)) {
                Icon(painterResource(R.drawable.redo), stringResource(R.string.redo))
            }
            IconButton(onClick = onReset, enabled = canReset ?: canUndo, colors = colors, modifier = Modifier.testTag(PanelTags.RESET)) {
                Icon(painterResource(R.drawable.restore_24dp), stringResource(R.string.reset_edit_menu))
            }
            // Before / after: held down, the original photo shows
            if (onCompare != null) {
                val description = stringResource(R.string.compare_original)
                val currentOnCompare by rememberUpdatedState(onCompare)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = description }
                        .testTag(EditorTags.COMPARE)
                        .pointerInput(Unit) {
                            detectTapGestures(onPress = {
                                currentOnCompare(true)
                                try {
                                    tryAwaitRelease()
                                } finally {
                                    currentOnCompare(false)
                                }
                            })
                        },
                ) {
                    Icon(painterResource(R.drawable.compare), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            // Handle (on the right): drag to move the controls, tap for the next corner (gestures from the caller)
            if (handleModifier != null) {
                val description = stringResource(R.string.move_controls)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = handleModifier
                        .size(32.dp, 48.dp)
                        .semantics { contentDescription = description }
                        .testTag(PanelTags.HISTORY_HANDLE),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_drag_handle_24dp),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
    previewEnd: (@Composable () -> Unit)? = null,
) {
    val color = Color(style.color)
    Column(modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (forText) {
            // Texts: fonts first; they are resized on the photo (handle or two fingers), not here
            FontChooser(style.font, onChoose = { onChange(style.copy(font = it)) })
            // "Murmely" in the chosen font and colour on the left, [previewEnd] (the layer buttons) on the right
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                // Drawn like on the photo: font, colour and effects
                val demo = TextItem("Murmely", 0f, 0f, size = 1f, color = style.color, font = style.font, effects = style.effects)
                Canvas(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics { text = AnnotatedString(demo.text) }
                        .testTag(PanelTags.STYLE_PREVIEW)
                ) {
                    val textSize = 32.sp.toPx()
                    val left = (TextGeometry.effectsExtent(demo).first + OverlayRenderer.textWidth(demo) / 2) * textSize
                    drawIntoCanvas { OverlayRenderer.drawTextAt(it.nativeCanvas, demo, textSize, left + 4.dp.toPx(), size.height / 2) }
                }
                previewEnd?.invoke()
            }
        } else {
            // Preview: a line of the chosen width (scaled to a typical photo width)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag(PanelTags.STYLE_PREVIEW),
            ) {
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
        if (forText) TextEffectsSection(style.effects, onChange = { onChange(style.copy(effects = it)) })
        if (!forText) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.tool_line_width),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(104.dp),
                )
                Slider(
                    value = style.strokeWidth,
                    onValueChange = { onChange(style.copy(strokeWidth = it)) },
                    valueRange = ToolStyle.STROKE_WIDTH_RANGE,
                    modifier = Modifier.weight(1f).testTag(PanelTags.SIZE),
                )
            }
        }
    }
}

/** A slider whose track shows a colour gradient */
@Composable
internal fun GradientSlider(
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

/** The font as a Compose font family (the bundled file, or the default font) */
@Composable
fun TextFont.fontFamily(): FontFamily = remember(this) { FontFamily(Typeface(TextTypefaces.typeface(this))) }

/** A chip per font, each name written in its own font */
@Composable
private fun FontChooser(selected: TextFont, onChoose: (TextFont) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
    ) {
        TextFont.entries.forEach { font ->
            FilterChip(
                selected = font == selected,
                onClick = { onChoose(font) },
                label = { Text(font.title ?: stringResource(R.string.font_default), fontFamily = font.fontFamily(), maxLines = 1) },
                modifier = Modifier.testTag(PanelTags.font(font)),
            )
        }
    }
}

/** Whether the selected text or sticker can go one layer to the back or to the front */
data class LayerState(val canGoBack: Boolean, val canGoForward: Boolean)

/** "Layer  [−] [+]": the selected item one layer to the back or to the front */
@Composable
private fun LayerButtons(state: LayerState, onLayer: (forward: Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.layer), style = MaterialTheme.typography.bodyMedium)
        val back = stringResource(R.string.layer_back)
        FilledTonalIconButton(
            onClick = { onLayer(false) },
            enabled = state.canGoBack,
            modifier = Modifier.semantics { contentDescription = back }.testTag(PanelTags.LAYER_BACK),
        ) { Text("\u2212", style = MaterialTheme.typography.titleLarge) }
        val forward = stringResource(R.string.layer_forward)
        FilledTonalIconButton(
            onClick = { onLayer(true) },
            enabled = state.canGoForward,
            modifier = Modifier.semantics { contentDescription = forward }.testTag(PanelTags.LAYER_FORWARD),
        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

/**
 * "Effects", folded away: outline (with its colour), letter spacing, soft shadow and drop
 * shadow (with colour and direction). Each slider at its start means off.
 */
@Composable
private fun TextEffectsSection(effects: TextEffects, onChange: (TextEffects) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { open = !open }
            .padding(vertical = 8.dp)
            .testTag(PanelTags.EFFECTS),
    ) {
        Text(stringResource(R.string.text_effects), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(if (open) "\u25B4" else "\u25BE", style = MaterialTheme.typography.titleSmall)
    }
    if (!open) return
    EffectSlider(R.string.effect_outline, effects.outline, TextEffects.OUTLINE_RANGE, PanelTags.OUTLINE) { onChange(effects.copy(outline = it)) }
    if (effects.outline > 0f) {
        ColorDots(effects.outlineColor, PanelTags.OUTLINE) { onChange(effects.copy(outlineColor = it)) }
    }
    EffectSlider(R.string.effect_letter_spacing, effects.letterSpacing, TextEffects.LETTER_SPACING_RANGE, PanelTags.LETTER_SPACING) {
        onChange(effects.copy(letterSpacing = it))
    }
    EffectSlider(R.string.effect_shadow, effects.shadow, TextEffects.SHADOW_RANGE, PanelTags.SHADOW) { onChange(effects.copy(shadow = it)) }
    EffectSlider(R.string.effect_drop_shadow, effects.dropShadow, TextEffects.DROP_SHADOW_RANGE, PanelTags.DROP_SHADOW) {
        onChange(effects.copy(dropShadow = it))
    }
    if (effects.dropShadow > 0f) {
        EffectSlider(R.string.effect_direction, effects.dropShadowAngle, TextEffects.ANGLE_RANGE, PanelTags.DROP_SHADOW_ANGLE) {
            onChange(effects.copy(dropShadowAngle = it))
        }
        ColorDots(effects.dropShadowColor, PanelTags.DROP_SHADOW) { onChange(effects.copy(dropShadowColor = it)) }
    }
}

@Composable
private fun EffectSlider(
    @StringRes label: Int,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    tag: String,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(104.dp),
        )
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f).testTag(tag))
    }
}

/** A row of colour dots; the chosen one has a ring */
@Composable
private fun ColorDots(selected: Int, tag: String, onChoose: (Int) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(start = 104.dp, bottom = 4.dp),
    ) {
        TextEffects.COLORS.forEach { c ->
            val ring = if (c == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .border(if (c == selected) 3.dp else 1.dp, ring, CircleShape)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .clickable { onChoose(c) }
                    .testTag(PanelTags.effectColor(tag, c)),
            )
        }
    }
}

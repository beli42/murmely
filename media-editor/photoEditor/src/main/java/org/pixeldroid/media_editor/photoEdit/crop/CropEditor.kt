package org.pixeldroid.media_editor.photoEdit.crop

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.pixeldroid.media_editor.photoEdit.R
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.widthIn
import org.pixeldroid.media_editor.photoEdit.panel.ResettableSlider
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.clipRect
import org.pixeldroid.media_editor.photoEdit.overlay.ImageFrame

/** Test tags of the crop editor */
object CropTags {
    const val STRAIGHTEN = "crop_straighten"
    const val CANVAS = "crop_canvas"
    const val ROTATE_LEFT = "crop_rotate_left"
    const val ROTATE_RIGHT = "crop_rotate_right"
    const val RESET = "crop_reset"
    fun aspect(aspect: CropAspect) = "crop_aspect_${aspect.name}"
}

/** Size of the preview shown while cropping (the result is made at full size) */
private const val PREVIEW_SIZE = 1600

/**
 * Cropping and turning [source] (the original image). Shows the whole image, turned, with the
 * crop frame: drag a corner or edge to resize it, inside to move it. Below: shapes and turning.
 * [settings] is the crop being made; every change goes to [onChange].
 *
 * @param busy the crop is being made at full size
 */
@Composable
fun CropEditor(
    source: Uri,
    settings: CropSettings,
    busy: Boolean,
    onChange: (CropSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val original by produceState<Bitmap?>(null, source) {
        value = withContext(Dispatchers.IO) {
            runCatching { CropRenderer.preview(context, source, PREVIEW_SIZE) }.getOrNull()
        }
    }
    val image = original
    // Shown turned
    val turned = remember(image, settings.quarterTurns) { image?.let { CropRenderer.turned(it, settings.quarterTurns) } }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        // Takes every touch, so nothing of the editor below reacts
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture { awaitFirstDown(requireUnconsumed = false).consume() }
        },
    ) {
        Column {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                if (turned == null) CircularProgressIndicator()
                else CropFrame(turned, settings, onChange, Modifier.fillMaxSize())
            }
            val enabled = image != null && !busy
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
            ) {
                CropAspect.entries.forEach { aspect ->
                    FilterChip(
                        selected = settings.aspect == aspect,
                        enabled = enabled,
                        onClick = { image?.let { onChange(CropGeometry.setAspect(settings, aspect, it.width, it.height)) } },
                        label = { Text(aspect.label()) },
                        modifier = Modifier.testTag(CropTags.aspect(aspect)),
                    )
                }
            }
            // Straighten: free angle, double tap on the handle back to 0°
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Text(stringResource(R.string.crop_straighten), style = MaterialTheme.typography.bodyMedium)
                ResettableSlider(
                    value = settings.angle,
                    onChange = { onChange(CropGeometry.straighten(settings, it)) },
                    range = -CropGeometry.MAX_ANGLE..CropGeometry.MAX_ANGLE,
                    resetValue = 0f,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp).testTag(CropTags.STRAIGHTEN),
                )
                Text(
                    // One decimal, without a locale-dependent format
                    "${(settings.angle * 10).roundToInt() / 10f}°",
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.widthIn(min = 48.dp),
                )
            }
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) {
                IconButton(
                    onClick = { image?.let { onChange(CropGeometry.turnBy(settings, -1, it.width, it.height)) } },
                    enabled = enabled,
                    modifier = Modifier.testTag(CropTags.ROTATE_LEFT),
                ) {
                    // The same arrow, mirrored
                    Icon(painterResource(R.drawable.ic_rotate_right_24dp), stringResource(R.string.crop_rotate_left), Modifier.scale(-1f, 1f))
                }
                IconButton(
                    onClick = { image?.let { onChange(CropGeometry.turnBy(settings, 1, it.width, it.height)) } },
                    enabled = enabled,
                    modifier = Modifier.testTag(CropTags.ROTATE_RIGHT),
                ) {
                    Icon(painterResource(R.drawable.ic_rotate_right_24dp), stringResource(R.string.crop_rotate_right))
                }
                IconButton(
                    onClick = { onChange(CropSettings()) },
                    enabled = enabled && settings != CropSettings(),
                    modifier = Modifier.testTag(CropTags.RESET),
                ) {
                    Icon(painterResource(R.drawable.restore_24dp), stringResource(R.string.crop_reset))
                }
            }
        }
    }
}

@Composable
private fun CropAspect.label(): String = when (this) {
    CropAspect.FREE -> stringResource(R.string.crop_aspect_free)
    CropAspect.ORIGINAL -> stringResource(R.string.crop_aspect_original)
    CropAspect.SQUARE -> "1:1"
    CropAspect.LANDSCAPE_4_3 -> "4:3"
    CropAspect.PORTRAIT_3_4 -> "3:4"
    CropAspect.LANDSCAPE_16_9 -> "16:9"
    CropAspect.PORTRAIT_9_16 -> "9:16"
}

/** The turned image, scaled to fit, with the crop frame on it; takes the drags */
@Composable
private fun CropFrame(turned: Bitmap, settings: CropSettings, onChange: (CropSettings) -> Unit, modifier: Modifier = Modifier) {
    val bitmap = remember(turned) { turned.asImageBitmap() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val frame = if (size.width > 0 && size.height > 0) ImageFrame(turned.width, turned.height, size.width, size.height) else null
    val currentSettings by rememberUpdatedState(settings)
    val currentOnChange by rememberUpdatedState(onChange)
    val frameColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier
            .onSizeChanged { size = it }
            .testTag(CropTags.CANVAS)
            .pointerInput(frame) {
                val f = frame ?: return@pointerInput
                // Touch area of the handles, relative to the image
                val tolerance = 24.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val start = f.toImage(down.position.x, down.position.y)
                    val s = currentSettings
                    val imageAspect = turned.width.toFloat() / turned.height
                    val ratio = s.aspect.pixelRatio(imageAspect)
                    val handle = CropGeometry.handleAt(
                        s.rect, start.x, start.y,
                        tolerance / f.shownWidth, tolerance / f.shownHeight, shapeFixed = ratio != null,
                    ) ?: return@awaitEachGesture
                    down.consume()
                    val startRect = s.rect
                    drag(down.id) { change ->
                        change.consume()
                        val now = f.toImage(change.position.x, change.position.y)
                        val rect = CropGeometry.drag(startRect, handle, now.x - start.x, now.y - start.y, ratio, imageAspect)
                        currentOnChange(currentSettings.copy(rect = rect))
                    }
                }
            }
    ) {
        val f = frame ?: return@Canvas
        val offset = IntOffset(f.sideBar, f.topBar)
        // Straightened like when saved: turned around the middle, enlarged to fill, clipped
        val angle = settings.angle
        val scale = CropGeometry.straightenScale(turned.width.toFloat(), turned.height.toFloat(), angle)
        val middle = Offset(f.sideBar + f.shownWidth / 2f, f.topBar + f.shownHeight / 2f)
        clipRect(offset.x.toFloat(), offset.y.toFloat(), (offset.x + f.shownWidth).toFloat(), (offset.y + f.shownHeight).toFloat()) {
            withTransform({
                rotate(angle, middle)
                scale(scale, scale, middle)
            }) {
                drawImage(
                    bitmap,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(turned.width, turned.height),
                    dstOffset = offset,
                    dstSize = IntSize(f.shownWidth, f.shownHeight),
                )
            }
        }
        val r = settings.rect
        val left = f.sideBar + r.left * f.shownWidth
        val top = f.topBar + r.top * f.shownHeight
        val right = f.sideBar + r.right * f.shownWidth
        val bottom = f.topBar + r.bottom * f.shownHeight
        drawOutside(Offset(offset.x.toFloat(), offset.y.toFloat()), Size(f.shownWidth.toFloat(), f.shownHeight.toFloat()), left, top, right, bottom)
        drawFrame(left, top, right, bottom, frameColor, fine = angle != 0f)
    }
}

/** Darkens the image outside the crop */
private fun DrawScope.drawOutside(origin: Offset, imageSize: Size, left: Float, top: Float, right: Float, bottom: Float) {
    val shade = Color.Black.copy(alpha = 0.55f)
    val imageRight = origin.x + imageSize.width
    val imageBottom = origin.y + imageSize.height
    drawRect(shade, origin, Size(imageSize.width, top - origin.y))
    drawRect(shade, Offset(origin.x, bottom), Size(imageSize.width, imageBottom - bottom))
    drawRect(shade, Offset(origin.x, top), Size(left - origin.x, bottom - top))
    drawRect(shade, Offset(right, top), Size(imageRight - right, bottom - top))
}

/** Frame, rule-of-thirds lines and corner marks */
private fun DrawScope.drawFrame(left: Float, top: Float, right: Float, bottom: Float, color: Color, fine: Boolean = false) {
    val thin = 1.dp.toPx()
    val thirdsColor = Color.White.copy(alpha = 0.6f)
    // While straightened: a fine grid to line up horizons and edges
    if (fine) {
        val fineColor = Color.White.copy(alpha = 0.25f)
        for (i in 1 until 9) {
            val x = left + (right - left) * i / 9
            val y = top + (bottom - top) * i / 9
            drawLine(fineColor, Offset(x, top), Offset(x, bottom), thin / 2)
            drawLine(fineColor, Offset(left, y), Offset(right, y), thin / 2)
        }
    }
    for (i in 1..2) {
        val x = left + (right - left) * i / 3
        val y = top + (bottom - top) * i / 3
        drawLine(thirdsColor, Offset(x, top), Offset(x, bottom), thin)
        drawLine(thirdsColor, Offset(left, y), Offset(right, y), thin)
    }
    drawRect(Color.White, Offset(left, top), Size(right - left, bottom - top), style = Stroke(2.dp.toPx()))
    // Corner marks
    val length = 20.dp.toPx()
    val width = 4.dp.toPx()
    for ((x, y, dx, dy) in listOf(
        listOf(left, top, 1f, 1f), listOf(right, top, -1f, 1f),
        listOf(left, bottom, 1f, -1f), listOf(right, bottom, -1f, -1f),
    )) {
        drawLine(color, Offset(x, y), Offset(x + dx * length, y), width)
        drawLine(color, Offset(x, y), Offset(x, y + dy * length), width)
    }
}

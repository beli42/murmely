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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import org.pixeldroid.media_editor.photoEdit.LocalLandscapePhotoShare
import org.pixeldroid.media_editor.photoEdit.EditorLayout
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.positionChanged
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
    const val ZOOM = "crop_zoom"
    fun fill(fill: StraightenFill) = "crop_fill_${fill.name}"
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
    // The view enlarged (zoom slider), around the crop's middle as it was when zooming
    var viewZoom by remember { mutableFloatStateOf(1f) }
    var focus by remember { mutableStateOf(Offset(0.5f, 0.5f)) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        // Takes every touch, so nothing of the editor below reacts
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture { awaitFirstDown(requireUnconsumed = false).consume() }
        },
    ) {
        val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        val enabled = image != null && !busy

        @Composable
        fun Photo(modifier: Modifier) = Box(modifier.padding(16.dp), contentAlignment = Alignment.Center) {
            if (turned == null) CircularProgressIndicator()
            else CropFrame(
                turned, settings, onChange, viewZoom, focus,
                onViewChange = { zoom, newFocus -> viewZoom = zoom; focus = newFocus },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Shapes, straighten, zoom, corners, turning
        @Composable
        fun Controls() {

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
                        onChange = { degrees -> image?.let { onChange(CropGeometry.straighten(settings, degrees, it.width, it.height)) } },
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
                // Zoom: the view enlarged around the crop's middle, to crop precisely; the crop
                // itself stays as it is. Double tap on the handle: the whole image again
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Text(stringResource(R.string.crop_zoom), style = MaterialTheme.typography.bodyMedium)
                    ResettableSlider(
                        value = viewZoom,
                        onChange = {
                            viewZoom = it
                            focus = Offset(settings.rect.centerX, settings.rect.centerY)
                        },
                        range = 1f..CropGeometry.MAX_VIEW_ZOOM,
                        resetValue = 1f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp).testTag(CropTags.ZOOM),
                    )
                    Text(
                        "${(viewZoom * 10).roundToInt() / 10f}×",
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.widthIn(min = 48.dp),
                    )
                }
                // Straightened: fill the frame, or keep all of the photo with see-through or black corners
                if (settings.angle != 0f) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    ) {
                        StraightenFill.entries.forEach { fill ->
                            FilterChip(
                                selected = settings.fill == fill,
                                enabled = enabled,
                                onClick = { image?.let { onChange(CropGeometry.withFill(settings, fill, it.width, it.height)) } },
                                label = { Text(stringResource(fill.label)) },
                                modifier = Modifier.testTag(CropTags.fill(fill)),
                            )
                        }
                    }
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

        Column {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (landscape) {
                // The photo on the left (the share from the settings), the controls on the right
                // as wide as the turned photo is at full height (at most the share), the rest for the controls
                val share = LocalLandscapePhotoShare.current
                val aspect = turned?.let { it.height.toFloat() / it.width }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                  val padding = 32f
                  val photoWidth = (EditorLayout.landscapePhotoWidth(maxWidth.value - padding, maxHeight.value - padding, aspect, share).dp + padding.dp)
                      .coerceAtMost(maxWidth * share)
                  Row(Modifier.fillMaxSize()) {
                    Photo(Modifier.width(photoWidth).fillMaxHeight())
                    Column(
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    ) {
                        Controls()
                    }
                  }
                }
            } else {
                Photo(Modifier.weight(1f).fillMaxWidth())
                Controls()
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
private fun CropFrame(
    turned: Bitmap,
    settings: CropSettings,
    onChange: (CropSettings) -> Unit,
    /** How much the view is enlarged, around [focus] (relative to the frame, see CropGeometry.viewCentre) */
    viewZoom: Float,
    focus: Offset,
    /** Two fingers moved or spread the view: its new zoom and focus */
    onViewChange: (zoom: Float, focus: Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(turned) { turned.asImageBitmap() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    // What is cut from: the image, or (straightened, not filled) the box around it
    val (frameW, frameH) = CropGeometry.frameSize(turned.width.toFloat(), turned.height.toFloat(), settings)
    val frame = if (size.width > 0 && size.height > 0) ImageFrame(frameW.roundToInt(), frameH.roundToInt(), size.width, size.height) else null
    val currentSettings by rememberUpdatedState(settings)
    val currentOnChange by rememberUpdatedState(onChange)
    val frameColor = MaterialTheme.colorScheme.primary
    // Enlarged view: a point p of the unenlarged view is shown at middle + (p - anchor) * zoom,
    // the anchor being the image point shown in the middle
    val zoom = viewZoom.coerceAtLeast(1f)
    fun anchor(f: ImageFrame) = Offset(
        f.sideBar + CropGeometry.viewCentre(focus.x, zoom) * f.shownWidth,
        f.topBar + CropGeometry.viewCentre(focus.y, zoom) * f.shownHeight,
    )
    val middleOfView = Offset(size.width / 2f, size.height / 2f)
    val toView by rememberUpdatedState { f: ImageFrame, x: Float, y: Float ->
        val a = anchor(f)
        Offset(a.x + (x - middleOfView.x) / zoom, a.y + (y - middleOfView.y) / zoom)
    }
    val currentZoom by rememberUpdatedState(zoom)
    val currentFocus by rememberUpdatedState(focus)
    val currentOnViewChange by rememberUpdatedState(onViewChange)

    Canvas(
        modifier
            .onSizeChanged { size = it }
            .clipToBounds()
            .testTag(CropTags.CANVAS)
            .pointerInput(frame) {
                val f = frame ?: return@pointerInput
                // Touch area of the handles, relative to the image
                val tolerance = 24.dp.toPx()
                // One finger: a corner, an edge or the whole crop. Two fingers: the view moved and
                // enlarged (like the zoom slider), so every part of the crop can be reached
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val z = currentZoom
                    fun toImage(position: Offset) = toView(f, position.x, position.y).let { f.toImage(it.x, it.y) }
                    val start = toImage(down.position)
                    val s = currentSettings
                    val imageAspect = frameW / frameH
                    val ratio = s.aspect.pixelRatio(imageAspect)
                    val handle = CropGeometry.handleAt(
                        s.rect, start.x, start.y,
                        tolerance / (f.shownWidth * z), tolerance / (f.shownHeight * z), shapeFixed = ratio != null,
                    )
                    if (handle != null) down.consume()
                    val startRect = s.rect
                    var twoFingers = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size >= 2) {
                            twoFingers = true
                            val newZoom = (currentZoom * event.calculateZoom()).coerceIn(1f, CropGeometry.MAX_VIEW_ZOOM)
                            // The image follows the fingers: the point in the middle moves the other way
                            val pan = event.calculatePan()
                            val now = currentFocus
                            val x = CropGeometry.viewCentre(CropGeometry.viewCentre(now.x, currentZoom) - pan.x / (f.shownWidth * currentZoom), newZoom)
                            val y = CropGeometry.viewCentre(CropGeometry.viewCentre(now.y, currentZoom) - pan.y / (f.shownHeight * currentZoom), newZoom)
                            currentOnViewChange(newZoom, Offset(x, y))
                            event.changes.forEach { it.consume() }
                            continue
                        }
                        // After two fingers, the rest of the touch does nothing
                        if (twoFingers || handle == null) continue
                        val change = pressed.firstOrNull { it.id == down.id } ?: continue
                        if (change.positionChanged()) {
                            change.consume()
                            val now = toImage(change.position)
                            val rect = CropGeometry.drag(startRect, handle, now.x - start.x, now.y - start.y, ratio, imageAspect)
                            currentOnChange(currentSettings.copy(rect = rect))
                        }
                    }
                }
            }
    ) {
        val f = frame ?: return@Canvas
        val offset = IntOffset(f.sideBar, f.topBar)
        val a = anchor(f)
        fun shown(x: Float, y: Float) = Offset(middleOfView.x + (x - a.x) * zoom, middleOfView.y + (y - a.y) * zoom)
        // Straightened like when saved: turned around the middle; filled (enlarged) or whole in
        // a larger frame, its empty corners black or see-through (shown as a checkerboard)
        val angle = settings.angle
        val fillScale = if (settings.fill == StraightenFill.ZOOM) CropGeometry.straightenScale(turned.width.toFloat(), turned.height.toFloat(), angle) else 1f
        val middle = Offset(f.sideBar + f.shownWidth / 2f, f.topBar + f.shownHeight / 2f)
        val k = f.shownWidth / frameW
        val imageW = turned.width * k
        val imageH = turned.height * k
        withTransform({
            translate(middleOfView.x - a.x * zoom, middleOfView.y - a.y * zoom)
            scale(zoom, zoom, Offset.Zero)
        }) { clipRect(offset.x.toFloat(), offset.y.toFloat(), (offset.x + f.shownWidth).toFloat(), (offset.y + f.shownHeight).toFloat()) {
            if (angle != 0f) when (settings.fill) {
                StraightenFill.BLACK -> drawRect(Color.Black, Offset(offset.x.toFloat(), offset.y.toFloat()), Size(f.shownWidth.toFloat(), f.shownHeight.toFloat()))
                StraightenFill.TRANSPARENT -> drawCheckerboard(Offset(offset.x.toFloat(), offset.y.toFloat()), Size(f.shownWidth.toFloat(), f.shownHeight.toFloat()))
                StraightenFill.ZOOM -> Unit
            }
            withTransform({
                rotate(angle, middle)
                scale(fillScale, fillScale, middle)
            }) {
                drawImage(
                    bitmap,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(turned.width, turned.height),
                    dstOffset = IntOffset((middle.x - imageW / 2).roundToInt(), (middle.y - imageH / 2).roundToInt()),
                    dstSize = IntSize(imageW.roundToInt(), imageH.roundToInt()),
                )
            }
        }
        }
        // The frame drawn on top, enlarged in place but with lines of the usual width
        val r = settings.rect
        val (left, top) = shown(f.sideBar + r.left * f.shownWidth, f.topBar + r.top * f.shownHeight)
        val (right, bottom) = shown(f.sideBar + r.right * f.shownWidth, f.topBar + r.bottom * f.shownHeight)
        val imageTopLeft = shown(offset.x.toFloat(), offset.y.toFloat())
        val imageBottomRight = shown((offset.x + f.shownWidth).toFloat(), (offset.y + f.shownHeight).toFloat())
        drawOutside(imageTopLeft, Size(imageBottomRight.x - imageTopLeft.x, imageBottomRight.y - imageTopLeft.y), left, top, right, bottom)
        drawFrame(left, top, right, bottom, frameColor, fine = angle != 0f)
    }
}

/** Light and dark squares: where the image will be see-through */
private fun DrawScope.drawCheckerboard(origin: Offset, area: Size) {
    val square = 8.dp.toPx()
    drawRect(Color(0xFFDDDDDD), origin, area)
    var y = 0
    while (y * square < area.height) {
        var x = y % 2
        while (x * square < area.width) {
            drawRect(
                Color(0xFFAAAAAA),
                Offset(origin.x + x * square, origin.y + y * square),
                Size(minOf(square, area.width - x * square), minOf(square, area.height - y * square)),
            )
            x += 2
        }
        y++
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

/** Name of each way to fill the corners */
private val StraightenFill.label: Int
    get() = when (this) {
        StraightenFill.ZOOM -> R.string.crop_fill_zoom
        StraightenFill.TRANSPARENT -> R.string.crop_fill_transparent
        StraightenFill.BLACK -> R.string.crop_fill_black
    }

package org.pixeldroid.media_editor.photoEdit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.updateBounds
import org.pixeldroid.media_editor.photoEdit.overlay.ItemBox
import org.pixeldroid.media_editor.photoEdit.overlay.ItemGeometry
import org.pixeldroid.media_editor.photoEdit.overlay.Overlay
import org.pixeldroid.media_editor.photoEdit.overlay.OverlayHitTest
import org.pixeldroid.media_editor.photoEdit.overlay.OverlayRenderer
import org.pixeldroid.media_editor.photoEdit.overlay.RelativePoint
import org.pixeldroid.media_editor.photoEdit.overlay.StickerBin
import org.pixeldroid.media_editor.photoEdit.overlay.StickerGeometry
import org.pixeldroid.media_editor.photoEdit.overlay.Stroke
import org.pixeldroid.media_editor.photoEdit.overlay.TextGeometry
import kotlin.math.hypot

/**
 * Shows the overlay (drawings, texts, stickers) and takes touch input:
 * - draw mode: draw lines
 * - text and sticker mode: tap an item to select it; drag it to move it, drag the handle at its
 *   corner to scale and turn it, or use two fingers to move, scale and turn it at once. While an
 *   item is dragged, a bin appears at the bottom: dropping it there removes it.
 *   Texts: tap empty space for a new text, tap the selected text to change it; both are typed
 *   right on the photo (see EditorScreen). Stickers are added from the list below the photo.
 *
 * The view covers exactly the shown image (see EditorScreen), so a touch at
 * (x, y) is at (x / width, y / height) on the image.
 */
class DrawingView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var model: PhotoEditViewModel? = null

    /** Sticker images by sticker id, loaded for this view's size (see EditorScreen) */
    private var stickerImages: Map<Long, Bitmap> = emptyMap()

    /** Line being drawn right now */
    private val currentPoints = mutableListOf<RelativePoint>()

    private val touchSlop = ViewConfiguration.get(context!!).scaledTouchSlop

    /** What the fingers do with a text or sticker */
    private enum class Gesture { MOVE, HANDLE, PINCH }

    /**
     * A text or sticker being changed by the fingers. [origin] is its box when the gesture began
     * (as stored), [phaseStart] and [touches] where it and the fingers were when the number of
     * fingers last changed, [box] where the fingers have it now.
     */
    private class ItemDrag(
        val id: Long,
        val isText: Boolean,
        val gesture: Gesture,
        val origin: ItemBox,
        val phaseStart: ItemBox,
        val touches: List<RelativePoint>,
        var box: ItemBox,
        var moved: Boolean,
        /** It was selected before this touch: tapping a selected text edits it */
        val wasSelected: Boolean,
        /** The finger is on the bin: letting go removes the item */
        var overBin: Boolean = false,
    )
    private var drag: ItemDrag? = null

    /** A touch on empty space (no item), to tell a tap from a drag */
    private var emptyTouch: RelativePoint? = null

    private val density = resources.displayMetrics.density

    /** Bin for removing texts and stickers: radius and distance from the bottom, in pixels */
    private val binRadius = 26 * density
    private val binMargin = 16 * density
    private val binPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val binIcon = AppCompatResources.getDrawable(context!!, R.drawable.delete)?.mutate()?.apply { setTint(Color.WHITE) }

    /** Size of the handle on screen */
    private val handleRadius = 14 * density
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
        color = Color.WHITE
        setShadowLayer(3 * density, 0f, 0f, Color.BLACK)
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        setShadowLayer(3 * density, 0f, 0f, Color.BLACK)
    }
    private val handleArrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
        strokeCap = Paint.Cap.ROUND
        color = Color.DKGRAY
    }

    /** Nothing is drawn (comparing with the original photo) */
    var overlayHidden: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val imageAspect get() = if (width == 0) 1f else height.toFloat() / width

    /** Texts and stickers can be selected and changed in this mode */
    private val itemMode: Boolean
        get() = model?.shownView?.value.let {
            it == PhotoEditViewModel.ShownView.Text || it == PhotoEditViewModel.ShownView.Sticker
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (overlayHidden) return
        val model = model ?: return
        val style = model.toolStyle.value
        // The text being typed is shown by the text field instead
        val editing = model.editingText.value?.id
        val overlay = withDragApplied(model.overlay.value).let { if (editing != null) it.removeText(editing) else it }
        OverlayRenderer.draw(
            canvas, width, height, overlay, stickerImages,
            inProgress = currentPoints.takeIf { it.isNotEmpty() }?.let { Stroke(it.toList(), style.strokeWidth, style.color) },
        )
        if (!itemMode) return
        val d = drag
        // Frame and handle of the selected item, only on screen
        if (d?.overBin != true) {
            model.selectedItem.value?.let { id -> itemBox(overlay, id)?.let { drawSelection(canvas, it) } }
        }
        if (d != null && d.moved && d.gesture == Gesture.MOVE) drawBin(canvas, d.overBin)
    }

    /** Box of the text or sticker [id] in [overlay] (a text with its measured width) */
    private fun itemBox(overlay: Overlay, id: Long): ItemBox? =
        overlay.stickers.firstOrNull { it.id == id }?.let { StickerGeometry.box(it) }
            ?: overlay.texts.firstOrNull { it.id == id }?.let { TextGeometry.box(it, OverlayRenderer.textWidth(it)) }

    /** The overlay with the dragged item where the fingers have it */
    private fun withDragApplied(overlay: Overlay): Overlay {
        val d = drag?.takeIf { it.moved } ?: return overlay
        return if (d.isText) {
            val text = overlay.texts.firstOrNull { it.id == d.id } ?: return overlay
            val t = TextGeometry.withBox(text, d.origin, d.box)
            overlay.transformText(t.id, t.x, t.y, t.size, t.rotation)
        } else {
            val sticker = overlay.stickers.firstOrNull { it.id == d.id } ?: return overlay
            val s = StickerGeometry.withBox(sticker, d.box)
            overlay.transformSticker(s.id, s.x, s.y, s.width, s.rotation)
        }
    }

    /** Frame around [box] and the handle at its bottom right corner (a turning arrow) */
    private fun drawSelection(canvas: Canvas, box: ItemBox) {
        val corners = ItemGeometry.corners(box, imageAspect).map { PointF(it.x * width, it.y * height) }
        val frame = Path().apply {
            moveTo(corners[0].x, corners[0].y)
            corners.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
        canvas.drawPath(frame, selectionPaint)
        val handle = corners[2]
        canvas.drawCircle(handle.x, handle.y, handleRadius, handlePaint)
        val arrow = handleRadius * 0.55f
        val arc = RectF(handle.x - arrow, handle.y - arrow, handle.x + arrow, handle.y + arrow)
        canvas.drawArc(arc, -60f, 270f, false, handleArrowPaint)
    }

    /** The bin at the bottom; red and larger while the item is over it */
    private fun drawBin(canvas: Canvas, active: Boolean) {
        val (cx, cy) = StickerBin.centre(width, height, binRadius, binMargin)
        val radius = if (active) binRadius * 1.2f else binRadius
        binPaint.color = if (active) Color.rgb(0xD3, 0x2F, 0x2F) else Color.argb(0xB0, 0, 0, 0)
        canvas.drawCircle(cx, cy, radius, binPaint)
        binIcon?.let {
            val half = (radius * 0.55f).toInt()
            it.updateBounds(cx.toInt() - half, cy.toInt() - half, cx.toInt() + half, cy.toInt() + half)
            it.draw(canvas)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (width == 0 || height == 0) return true
        when (model?.shownView?.value) {
            PhotoEditViewModel.ShownView.Draw -> touchEventDraw(event, pointOf(event, 0))
            PhotoEditViewModel.ShownView.Text -> touchEventItems(event, isText = true)
            PhotoEditViewModel.ShownView.Sticker -> touchEventItems(event, isText = false)
            else -> return true
        }
        invalidate()
        return true
    }

    private fun touchEventDraw(event: MotionEvent, point: RelativePoint) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                currentPoints.clear()
                currentPoints += point
            }
            MotionEvent.ACTION_MOVE -> currentPoints += point
            MotionEvent.ACTION_UP -> {
                currentPoints += point
                model?.addStroke(currentPoints.toList())
                currentPoints.clear()
            }
            MotionEvent.ACTION_CANCEL -> currentPoints.clear()
        }
    }

    /** Finger [index] of [event], relative to the image */
    private fun pointOf(event: MotionEvent, index: Int) = RelativePoint(event.getX(index) / width, event.getY(index) / height)

    /** All fingers of [event] that stay down (without the one going up) */
    private fun remainingPoints(event: MotionEvent): List<RelativePoint> {
        val leaving = if (event.actionMasked == MotionEvent.ACTION_POINTER_UP) event.actionIndex else -1
        return (0 until event.pointerCount).filter { it != leaving }.map { pointOf(event, it) }
    }

    /**
     * Text and sticker mode: select an item by touching it; one finger moves it (or scales and
     * turns it on the handle), two fingers move, scale and turn it. Each gesture is one step in
     * the history. Taps: on empty space a new text (text mode) or deselecting, on the selected
     * text changing it.
     */
    private fun touchEventItems(event: MotionEvent, isText: Boolean) {
        val model = model ?: return
        val overlay = model.overlay.value
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Touching the photo ends typing
                model.finishTextEdit()
                val current = model.overlay.value
                val point = pointOf(event, 0)
                val selectedId = model.selectedItem.value
                // Only items of this tool can be selected here
                val selectedBox = selectedId?.takeIf { id ->
                    if (isText) current.texts.any { it.id == id } else current.stickers.any { it.id == id }
                }?.let { itemBox(current, it) }
                val onHandle = selectedBox != null && ItemGeometry.handle(selectedBox, imageAspect).let {
                    hypot((it.x - point.x) * width, (it.y - point.y) * height) <= handleRadius * 1.6f
                }
                val hit = if (onHandle) selectedId else if (isText) {
                    OverlayHitTest.textAt(current, point, imageAspect, OverlayRenderer::textWidth)?.id
                } else {
                    OverlayHitTest.stickerAt(current, point, imageAspect)?.id
                }
                val box = hit?.let { itemBox(current, it) }
                drag = if (hit != null && box != null) {
                    ItemDrag(
                        hit, isText, if (onHandle) Gesture.HANDLE else Gesture.MOVE,
                        origin = box, phaseStart = box, touches = listOf(point), box = box,
                        moved = false, wasSelected = hit == selectedId,
                    )
                } else null
                emptyTouch = if (drag == null) point else null
                if (hit != null) model.selectItem(hit)
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                val d = drag ?: return
                // Start again from where the item is now, with the fingers that are down
                val points = remainingPoints(event)
                val gesture = if (points.size >= 2) Gesture.PINCH else Gesture.MOVE
                drag = ItemDrag(
                    d.id, d.isText, gesture, d.origin, phaseStart = d.box, touches = points.take(2), box = d.box,
                    moved = d.moved, wasSelected = d.wasSelected,
                )
            }
            MotionEvent.ACTION_MOVE -> {
                val d = drag
                if (d == null) {
                    // A drag on empty space is no tap
                    emptyTouch?.let { start ->
                        val p = pointOf(event, 0)
                        if (hypot((p.x - start.x) * width, (p.y - start.y) * height) > touchSlop) emptyTouch = null
                    }
                    return
                }
                val now = (0 until event.pointerCount).map { pointOf(event, it) }
                val box = when (d.gesture) {
                    Gesture.MOVE -> ItemGeometry.move(d.phaseStart, d.touches[0], now[0])
                    Gesture.HANDLE -> ItemGeometry.scaleAndTurn(d.phaseStart, d.touches[0], now[0], imageAspect)
                    Gesture.PINCH -> if (now.size >= 2)
                        ItemGeometry.pinch(d.phaseStart, d.touches[0], d.touches[1], now[0], now[1], imageAspect)
                    else d.box
                }
                // Texts can't leave the image: the saved image would cut them off
                d.box = if (d.isText) ItemGeometry.keepInside(box, imageAspect) else box
                if (!d.moved) {
                    val distance = hypot((now[0].x - d.touches[0].x) * width, (now[0].y - d.touches[0].y) * height)
                    d.moved = d.gesture == Gesture.PINCH || distance > touchSlop
                }
                // Only one finger moving the item can drop it on the bin
                val overBin = d.moved && d.gesture == Gesture.MOVE &&
                    StickerBin.contains(now[0].x * width, now[0].y * height, width, height, binRadius, binMargin)
                if (overBin && !d.overBin) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                d.overBin = overBin
            }
            MotionEvent.ACTION_UP -> {
                val d = drag
                val tap = emptyTouch
                if (d == null) {
                    if (tap != null) tapOnEmptySpace(model, isText, tap)
                } else when {
                    d.overBin -> if (d.isText) model.removeText(d.id) else model.removeSticker(d.id)
                    d.moved -> commit(model, overlay, d)
                    // Tapped the selected text: change it
                    d.isText && d.wasSelected -> model.startEditText(d.id)
                }
                drag = null
                emptyTouch = null
            }
            MotionEvent.ACTION_CANCEL -> {
                drag = null
                emptyTouch = null
            }
        }
    }

    /** Records the moved, scaled or turned item (nothing if it ended where it was) */
    private fun commit(model: PhotoEditViewModel, overlay: Overlay, d: ItemDrag) {
        if (d.isText) {
            val text = overlay.texts.firstOrNull { it.id == d.id } ?: return
            val changed = TextGeometry.withBox(text, d.origin, d.box)
            if (changed != text) model.transformText(changed)
        } else {
            val sticker = overlay.stickers.firstOrNull { it.id == d.id } ?: return
            val changed = StickerGeometry.withBox(sticker, d.box)
            if (changed != sticker) model.transformSticker(changed)
        }
    }

    /** A tap on empty space deselects; in text mode with nothing selected it starts a new text */
    private fun tapOnEmptySpace(model: PhotoEditViewModel, isText: Boolean, point: RelativePoint) {
        if (model.selectedItem.value != null) model.selectItem(null)
        else if (isText) model.startNewText(point.x, point.y)
    }

    /** Sticker images for this view's size, by sticker id */
    fun setStickerImages(images: Map<Long, Bitmap>) {
        stickerImages = images
        invalidate()
    }

    fun reset() {
        currentPoints.clear()
        drag = null
        emptyTouch = null
        invalidate()
    }

    fun setModel(model: PhotoEditViewModel) {
        this.model = model
    }
}

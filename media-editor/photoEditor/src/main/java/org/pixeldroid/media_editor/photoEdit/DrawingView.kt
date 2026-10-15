package org.pixeldroid.media_editor.photoEdit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.EditText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.pixeldroid.media_editor.photoEdit.overlay.Overlay
import org.pixeldroid.media_editor.photoEdit.overlay.OverlayHitTest
import org.pixeldroid.media_editor.photoEdit.overlay.OverlayRenderer
import org.pixeldroid.media_editor.photoEdit.overlay.RelativePoint
import org.pixeldroid.media_editor.photoEdit.overlay.Stroke
import org.pixeldroid.media_editor.photoEdit.overlay.StickerItem
import org.pixeldroid.media_editor.photoEdit.overlay.StickerGeometry
import org.pixeldroid.media_editor.photoEdit.overlay.TextItem
import kotlin.math.hypot

/**
 * Shows the overlay (drawings, texts, stickers) and takes touch input:
 * - draw mode: draw lines
 * - text mode: tap empty space for a new text, tap a text to edit it, drag a text to move it
 * - sticker mode: tap a sticker to select it; drag it to move it, drag the handle at its
 *   corner to scale and turn it, or use two fingers to move, scale and turn it at once
 *   (stickers are added from the sticker list below the photo)
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

    /** A text being touched; moved once the finger travelled more than the touch slop */
    private data class Grab(val id: Long, val start: RelativePoint, val itemX: Float, val itemY: Float)
    private var grab: Grab? = null
    private var grabMoved = false
    private var grabPosition: RelativePoint? = null

    private val touchSlop = ViewConfiguration.get(context!!).scaledTouchSlop

    /** What the fingers do with the selected sticker */
    private enum class StickerGesture { MOVE, HANDLE, PINCH }

    /**
     * A sticker gesture: the sticker as it was when it started (or when the number of fingers
     * changed) and where the finger(s) were then; [current] is the sticker as the fingers have it now.
     */
    private class StickerDrag(
        val gesture: StickerGesture,
        val start: StickerItem,
        val touches: List<RelativePoint>,
        var current: StickerItem,
        var moved: Boolean,
    )
    private var stickerDrag: StickerDrag? = null

    /** Size of the handle on screen */
    private val handleRadius = 14 * resources.displayMetrics.density
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * resources.displayMetrics.density
        color = Color.WHITE
        setShadowLayer(3 * resources.displayMetrics.density, 0f, 0f, Color.BLACK)
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        setShadowLayer(3 * resources.displayMetrics.density, 0f, 0f, Color.BLACK)
    }
    private val handleArrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        color = Color.DKGRAY
    }

    private val imageAspect get() = if (width == 0) 1f else height.toFloat() / width

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val model = model ?: return
        val style = model.toolStyle.value
        val overlay = withGrabApplied(model.overlay.value)
        OverlayRenderer.draw(
            canvas, width, height, overlay, stickerImages,
            inProgress = currentPoints.takeIf { it.isNotEmpty() }?.let { Stroke(it.toList(), style.strokeWidth, style.color) },
        )
        // Frame and handle of the selected sticker, only on screen
        if (model.shownView.value == PhotoEditViewModel.ShownView.Sticker) {
            overlay.stickers.firstOrNull { it.id == model.selectedSticker.value }?.let { drawSelection(canvas, it) }
        }
    }

    /** Frame around [sticker] and the handle at its bottom right corner (a turning arrow) */
    private fun drawSelection(canvas: Canvas, sticker: StickerItem) {
        val corners = StickerGeometry.corners(sticker, imageAspect).map { PointF(it.x * width, it.y * height) }
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

    /** The overlay with the grabbed item at the finger's position, while dragging */
    private fun withGrabApplied(overlay: Overlay): Overlay {
        stickerDrag?.let { drag ->
            if (!drag.moved) return overlay
            val s = drag.current
            return overlay.transformSticker(s.id, s.x, s.y, s.width, s.rotation)
        }
        val grab = grab ?: return overlay
        val position = grabPosition ?: return overlay
        if (!grabMoved) return overlay
        return overlay.moveText(grab.id, position.x, position.y)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (width == 0 || height == 0) return true
        val point = RelativePoint(event.x / width, event.y / height)
        when (model?.shownView?.value) {
            PhotoEditViewModel.ShownView.Draw -> touchEventDraw(event, point)
            PhotoEditViewModel.ShownView.Text -> touchEventItems(event, point)
            PhotoEditViewModel.ShownView.Sticker -> touchEventStickers(event)
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
     * Sticker mode: select a sticker by touching it; one finger moves it (or scales and turns it
     * on the handle), two fingers move, scale and turn it. Each gesture is one step in the history.
     */
    private fun touchEventStickers(event: MotionEvent) {
        val model = model ?: return
        val overlay = model.overlay.value
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val point = pointOf(event, 0)
                val selected = overlay.stickers.firstOrNull { it.id == model.selectedSticker.value }
                val onHandle = selected != null && StickerGeometry.handle(selected, imageAspect).let {
                    hypot((it.x - point.x) * width, (it.y - point.y) * height) <= handleRadius * 1.6f
                }
                val sticker = if (onHandle) selected else OverlayHitTest.stickerAt(overlay, point, imageAspect)
                model.selectSticker(sticker?.id)
                stickerDrag = sticker?.let {
                    StickerDrag(if (onHandle) StickerGesture.HANDLE else StickerGesture.MOVE, it, listOf(point), it, moved = false)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                val drag = stickerDrag ?: return
                // Start again from where the sticker is now, with the fingers that are down
                val points = remainingPoints(event)
                val gesture = if (points.size >= 2) StickerGesture.PINCH else StickerGesture.MOVE
                stickerDrag = StickerDrag(gesture, drag.current, points.take(2), drag.current, drag.moved)
            }
            MotionEvent.ACTION_MOVE -> {
                val drag = stickerDrag ?: return
                val now = (0 until event.pointerCount).map { pointOf(event, it) }
                drag.current = when (drag.gesture) {
                    StickerGesture.MOVE -> StickerGeometry.move(drag.start, drag.touches[0], now[0])
                    StickerGesture.HANDLE -> StickerGeometry.scaleAndTurn(drag.start, drag.touches[0], now[0], imageAspect)
                    StickerGesture.PINCH -> if (now.size >= 2)
                        StickerGeometry.pinch(drag.start, drag.touches[0], drag.touches[1], now[0], now[1], imageAspect)
                    else drag.current
                }
                if (!drag.moved) {
                    val distance = hypot((now[0].x - drag.touches[0].x) * width, (now[0].y - drag.touches[0].y) * height)
                    drag.moved = drag.gesture == StickerGesture.PINCH || distance > touchSlop
                }
            }
            MotionEvent.ACTION_UP -> {
                val drag = stickerDrag
                val stored = drag?.let { d -> overlay.stickers.firstOrNull { it.id == d.current.id } }
                // Only a real change is a step in the history (not a tap)
                if (drag != null && drag.moved && stored != null && drag.current != stored) model.transformSticker(drag.current)
                stickerDrag = null
            }
            MotionEvent.ACTION_CANCEL -> stickerDrag = null
        }
    }

    /** Text mode: grab and move texts, or tap to add/edit */
    private fun touchEventItems(event: MotionEvent, point: RelativePoint) {
        val model = model ?: return
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                grab = OverlayHitTest.textAt(model.overlay.value, point, imageAspect, ::textWidth)
                    ?.let { Grab(it.id, point, it.x, it.y) }
                grabMoved = false
                grabPosition = null
            }
            MotionEvent.ACTION_MOVE -> grab?.let { g ->
                val distance = hypot((point.x - g.start.x) * width, (point.y - g.start.y) * height)
                if (distance > touchSlop) grabMoved = true
                if (grabMoved) grabPosition = RelativePoint(g.itemX + point.x - g.start.x, g.itemY + point.y - g.start.y)
            }
            MotionEvent.ACTION_UP -> {
                val g = grab
                val position = grabPosition
                when {
                    // Moved: one step in the history
                    g != null && grabMoved && position != null -> model.moveText(g.id, position.x, position.y)
                    // Tapped a text: edit it
                    g != null -> model.overlay.value.texts.firstOrNull { it.id == g.id }?.let(::editText)
                    // Tapped empty space: add
                    else -> askForText(point)
                }
                grab = null
                grabPosition = null
            }
            MotionEvent.ACTION_CANCEL -> {
                grab = null
                grabPosition = null
            }
        }
    }

    /** Width of a text relative to the image width, measured with the font used for drawing */
    private fun textWidth(text: TextItem): Float {
        if (width == 0) return 0f
        val paint = Paint().apply { textSize = text.size * width }
        return paint.measureText(text.text) / width
    }

    private fun askForText(point: RelativePoint) {
        val editText = EditText(context)
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.add_text)
            .setMessage(R.string.text_to_add_to_image)
            .setView(editText)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                model?.addText(editText.text.toString(), point.x, point.y)
                invalidate()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> }
            .show()
    }

    private fun editText(text: TextItem) {
        val editText = EditText(context).apply {
            setText(text.text)
            setSelection(text.text.length)
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.edit_text)
            .setMessage(R.string.edit_text_empty_deletes)
            .setView(editText)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val changed = editText.text.toString()
                if (changed != text.text) model?.editText(text.id, changed)
                invalidate()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> }
            .show()
    }

    /** Sticker images for this view's size, by sticker id */
    fun setStickerImages(images: Map<Long, Bitmap>) {
        stickerImages = images
        invalidate()
    }

    fun reset() {
        currentPoints.clear()
        stickerDrag = null
        grab = null
        grabPosition = null
        invalidate()
    }

    fun setModel(model: PhotoEditViewModel) {
        this.model = model
    }
}

package org.pixeldroid.media_editor.photoEdit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
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
import org.pixeldroid.media_editor.photoEdit.overlay.TextItem
import kotlin.math.hypot

/**
 * Shows the overlay (drawings, texts, stickers) and takes touch input:
 * - draw mode: draw lines
 * - text mode: tap empty space for a new text, tap a text to edit it, drag a text to move it
 * - sticker mode: tap empty space to add a sticker, drag a sticker to move it
 *
 * The view covers exactly the shown image (see PhotoEditActivity.initDrawView), so a touch at
 * (x, y) is at (x / width, y / height) on the image.
 */
class DrawingView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var model: PhotoEditViewModel? = null

    /** Sticker images by uri, loaded for this view's size (see PhotoEditActivity) */
    private var stickerImages: Map<String, Bitmap> = emptyMap()

    /** Line being drawn right now */
    private val currentPoints = mutableListOf<RelativePoint>()

    /** A text or sticker being touched; moved once the finger travelled more than the touch slop */
    private data class Grab(val isText: Boolean, val id: Long, val start: RelativePoint, val itemX: Float, val itemY: Float)
    private var grab: Grab? = null
    private var grabMoved = false
    private var grabPosition: RelativePoint? = null

    private val touchSlop = ViewConfiguration.get(context!!).scaledTouchSlop

    private val imageAspect get() = if (width == 0) 1f else height.toFloat() / width

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val model = model ?: return
        val style = model.toolStyle.value
        OverlayRenderer.draw(
            canvas, width, height, withGrabApplied(model.overlay.value), stickerImages,
            inProgress = currentPoints.takeIf { it.isNotEmpty() }?.let { Stroke(it.toList(), style.strokeWidth, style.color) },
        )
    }

    /** The overlay with the grabbed item at the finger's position, while dragging */
    private fun withGrabApplied(overlay: Overlay): Overlay {
        val grab = grab ?: return overlay
        val position = grabPosition ?: return overlay
        if (!grabMoved) return overlay
        return if (grab.isText) overlay.moveText(grab.id, position.x, position.y)
        else overlay.moveSticker(grab.id, position.x, position.y)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (width == 0 || height == 0) return true
        val point = RelativePoint(event.x / width, event.y / height)
        when (model?.shownView?.value) {
            PhotoEditViewModel.ShownView.Draw -> touchEventDraw(event, point)
            PhotoEditViewModel.ShownView.Text, PhotoEditViewModel.ShownView.Sticker -> touchEventItems(event, point)
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

    /** Text and sticker mode: grab and move items, or tap to add/edit */
    private fun touchEventItems(event: MotionEvent, point: RelativePoint) {
        val model = model ?: return
        val isText = model.shownView.value == PhotoEditViewModel.ShownView.Text
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val overlay = model.overlay.value
                grab = if (isText) {
                    OverlayHitTest.textAt(overlay, point, imageAspect, ::textWidth)
                        ?.let { Grab(true, it.id, point, it.x, it.y) }
                } else {
                    OverlayHitTest.stickerAt(overlay, point, imageAspect)
                        ?.let { Grab(false, it.id, point, it.x, it.y) }
                }
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
                    g != null && grabMoved && position != null ->
                        if (g.isText) model.moveText(g.id, position.x, position.y)
                        else model.moveSticker(g.id, position.x, position.y)
                    // Tapped a text: edit it
                    g != null && g.isText -> model.overlay.value.texts.firstOrNull { it.id == g.id }?.let(::editText)
                    // Tapped a sticker: nothing to do (yet)
                    g != null -> Unit
                    // Tapped empty space: add
                    isText -> askForText(point)
                    else -> model.chooseSticker(point.x, point.y)
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

    /** Sticker images for this view's size */
    fun setStickerImages(images: Map<String, Bitmap>) {
        stickerImages = images
        invalidate()
    }

    fun reset() {
        currentPoints.clear()
        grab = null
        grabPosition = null
        invalidate()
    }

    fun setModel(model: PhotoEditViewModel) {
        this.model = model
    }
}

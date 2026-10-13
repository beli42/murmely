package org.pixeldroid.media_editor.photoEdit

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.pixeldroid.media_editor.photoEdit.overlay.OverlayRenderer
import org.pixeldroid.media_editor.photoEdit.overlay.RelativePoint
import org.pixeldroid.media_editor.photoEdit.overlay.Stroke

/**
 * Shows the drawings and texts of the overlay and takes touch input for drawing, text and
 * stickers. The view covers exactly the shown image (see PhotoEditActivity.initDrawView), so a
 * touch at (x, y) is at (x / width, y / height) on the image.
 */
class DrawingView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var model: PhotoEditViewModel? = null

    /** Line being drawn right now */
    private val currentPoints = mutableListOf<RelativePoint>()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val overlay = model?.overlay?.value ?: return
        // Stickers are shown by image views on top, see PhotoEditActivity.showStickers
        OverlayRenderer.draw(
            canvas, width, height, overlay.copy(stickers = emptyList()),
            inProgress = currentPoints.takeIf { it.isNotEmpty() }?.let { Stroke(it.toList()) },
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (width == 0 || height == 0) return true
        val point = RelativePoint(event.x / width, event.y / height)
        when (model?.shownView?.value) {
            PhotoEditViewModel.ShownView.Draw -> touchEventDraw(event, point)
            PhotoEditViewModel.ShownView.Text -> if (event.action == MotionEvent.ACTION_UP) askForText(point)
            PhotoEditViewModel.ShownView.Sticker ->
                if (event.action == MotionEvent.ACTION_UP) model?.chooseSticker(point.x, point.y)
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

    fun reset() {
        currentPoints.clear()
        invalidate()
    }

    fun setModel(model: PhotoEditViewModel) {
        this.model = model
    }
}

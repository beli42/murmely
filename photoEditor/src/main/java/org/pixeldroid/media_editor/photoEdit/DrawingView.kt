package org.pixeldroid.media_editor.photoEdit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class DrawingView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {
    val paint = Paint()
    val textPaint = Paint()

    private var model: PhotoEditViewModel? = null

    //TODO
    // How to do editing of previous text? Moving text? Draggable UI thingie at begin of text (x, y)?

    init {
        paint.apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 5f
            isAntiAlias = true
        }
        textPaint.apply {
            color = Color.RED
            style = Paint.Style.FILL
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        model?.drawingPath?.let {
            canvas.drawPath(it, paint)
        }
        model?.textList?.forEach { positionString ->
            canvas.drawText(positionString.string,
                positionString.x * width, positionString.y * height, //TODO convert back from percentage
                textPaint.apply { textSize = (width * 0.1).toFloat() }
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {

        when(model?.shownView?.value){
            PhotoEditViewModel.ShownView.Draw -> touchEventDraw(event)
            PhotoEditViewModel.ShownView.Text -> touchEventText(event)
            PhotoEditViewModel.ShownView.Sticker -> touchEventSticker(event)
            else -> return true
        }

        invalidate() // Request a redraw
        return true
    }

    private fun touchEventDraw(event: MotionEvent) {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> model?.drawingPath?.moveTo(x, y)
            MotionEvent.ACTION_MOVE -> model?.drawingPath?.lineTo(x, y)
            MotionEvent.ACTION_UP -> {}
        }
    }

    private fun touchEventText(event: MotionEvent) {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {}
            MotionEvent.ACTION_MOVE -> {} //TODO
            MotionEvent.ACTION_UP ->  {
                val editText = EditText(context)

                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.add_text)
                    .setMessage(R.string.text_to_add_to_image)
                    .setView(editText)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        val text = editText.text.toString()
                        model?.addTextAt(text, x/this@DrawingView.width, y/this@DrawingView.height)
                        this@DrawingView.invalidate()
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> }
                    .show()
            }
        }
    }

    private fun touchEventSticker(event: MotionEvent) {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {}
            MotionEvent.ACTION_MOVE -> {}
            MotionEvent.ACTION_UP -> {
                println("LOLOLOLOL")
                model?.chooseSticker(x, y)
            }
        }
    }

    fun reset() {
        model?.drawingPath?.reset()
        invalidate()
    }

    fun setModel(model: PhotoEditViewModel){
        this.model = model
    }
}
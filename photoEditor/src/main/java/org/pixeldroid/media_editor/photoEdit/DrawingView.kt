package org.pixeldroid.media_editor.photoEdit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View


class DrawingView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {
    val paint = Paint()
    val path: Path = Path()
    var drawEnabled = true

    init {
        paint.color = Color.RED
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        paint.isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (drawEnabled) canvas.drawPath(path, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if(!drawEnabled) return true

        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> path.moveTo(x, y)
            MotionEvent.ACTION_MOVE -> path.lineTo(x, y)
            MotionEvent.ACTION_UP -> {}
        }
        invalidate() // Request a redraw
        return true
    }

    fun setDrawing(paint: Paint, path: Path){
        paint.set(paint)
        path.set(path)
        invalidate()
    }

    fun reset() {
        path.reset()
        invalidate()
    }
}
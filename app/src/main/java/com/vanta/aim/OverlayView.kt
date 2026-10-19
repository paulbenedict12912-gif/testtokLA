package com.vanta.aim

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View

class OverlayView(context: Context) : View(context) {

    var targets: List<Detection> = emptyList()
    var locked: Detection? = null
    var onToggle: (() -> Unit)? = null
    var onRelease: (() -> Unit)? = null

    private val fovPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.argb(180, 0, 255, 160)
    }
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.argb(220, 255, 80, 80)
    }
    private val lockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.argb(255, 255, 220, 0)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(60, 0, 0, 0)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = 220f

        canvas.drawCircle(cx, cy, radius, fovPaint)

        for (d in targets) {
            val l = d.cx - d.w / 2
            val t = d.cy - d.h / 2
            canvas.drawRect(l, t, l + d.w, t + d.h, boxPaint)
        }

        locked?.let { d ->
            val l = d.cx - d.w / 2 - 6
            val t = d.cy - d.h / 2 - 6
            canvas.drawRect(l, t, l + d.w + 12, t + d.h + 12, lockPaint)
            canvas.drawCircle(d.cx, d.cy, 10f, lockPaint)
        }

        canvas.drawCircle(120f, height - 160f, 56f, fillPaint)
        canvas.drawCircle(120f, height - 160f, 56f, fovPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val dx = event.x - 120f
            val dy = event.y - (height - 160f)
            if (dx * dx + dy * dy < 56f * 56f) {
                onToggle?.invoke()
                performClick()
                return true
            }
            if (event.y < 120f) {
                onRelease?.invoke()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}

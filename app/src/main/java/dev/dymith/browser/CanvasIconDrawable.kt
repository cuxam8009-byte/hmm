package dev.dymith.browser

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * Icon toolbar được vẽ trực tiếp trên Canvas. Paint/Path/RectF được tái sử dụng,
 * không cấp phát đối tượng trong draw() để tránh GC khi cuộn/animate.
 */
internal class CanvasIconDrawable(
    private val kind: String,
    color: Int
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = 1.9f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val save = canvas.save()
        canvas.translate(b.left.toFloat(), b.top.toFloat())
        val scale = minOf(b.width(), b.height()) / 24f
        canvas.translate((b.width() - 24f * scale) / 2f, (b.height() - 24f * scale) / 2f)
        canvas.scale(scale, scale)
        when (kind) {
            "search" -> {
                canvas.drawCircle(10.5f, 10.5f, 6.2f, paint)
                canvas.drawLine(15.2f, 15.2f, 20.3f, 20.3f, paint)
            }
            "refresh" -> {
                rect.set(4f, 4f, 20f, 20f)
                canvas.drawArc(rect, 42f, 285f, false, paint)
                path.reset()
                path.moveTo(17.2f, 3.8f); path.lineTo(20.3f, 4.2f); path.lineTo(19.7f, 7.3f)
                canvas.drawPath(path, paint)
            }
            "close" -> {
                canvas.drawLine(6f, 6f, 18f, 18f, paint)
                canvas.drawLine(18f, 6f, 6f, 18f, paint)
            }
            "more" -> {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(12f, 5f, 1.55f, paint)
                canvas.drawCircle(12f, 12f, 1.55f, paint)
                canvas.drawCircle(12f, 19f, 1.55f, paint)
                paint.style = Paint.Style.STROKE
            }
        }
        canvas.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

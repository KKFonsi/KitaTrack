package com.example.kitatrack.ui.reports.chart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.kitatrack.R
import kotlin.math.abs
import kotlin.math.max

class LineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.kitatrack_divider)
        strokeWidth = 2f
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.kitatrack_primary_green)
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.kitatrack_primary_green)
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.kitatrack_secondary_text)
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }
    private var entries: List<ChartEntry> = emptyList()

    fun submitData(entries: List<ChartEntry>, negativeAware: Boolean = false) {
        this.entries = entries
        linePaint.color = ContextCompat.getColor(
            context,
            if (negativeAware && entries.any { it.value < 0 }) R.color.kitatrack_expense_red else R.color.kitatrack_primary_green
        )
        pointPaint.color = linePaint.color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (entries.isEmpty()) return
        val left = paddingLeft.toFloat() + 12f
        val top = paddingTop.toFloat() + 12f
        val right = width - paddingRight.toFloat() - 12f
        val bottom = height - paddingBottom.toFloat() - 34f
        val values = entries.map { it.value }
        val maxAbs = max(values.maxOf { abs(it) }, 1L)
        val zeroY = if (values.any { it < 0 }) (top + bottom) / 2 else bottom
        canvas.drawLine(left, zeroY, right, zeroY, gridPaint)
        val path = Path()
        val step = if (entries.size == 1) 0f else (right - left) / (entries.size - 1)
        entries.forEachIndexed { index, item ->
            val x = if (entries.size == 1) (left + right) / 2 else left + step * index
            val y = zeroY - ((item.value.toFloat() / maxAbs) * if (values.any { it < 0 }) (bottom - top) / 2 else (bottom - top))
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            canvas.drawCircle(x, y, 6f, pointPaint)
            canvas.drawText(item.label, x, height - paddingBottom.toFloat() - 6f, labelPaint)
        }
        canvas.drawPath(path, linePaint)
    }
}

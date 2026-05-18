package com.example.kitatrack.ui.reports.chart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.kitatrack.R
import kotlin.math.max

class ColumnChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.kitatrack_divider)
        strokeWidth = 2f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.kitatrack_secondary_text)
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.kitatrack_primary_text)
        textSize = 26f
        textAlign = Paint.Align.CENTER
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var entries: List<ChartEntry> = emptyList()

    fun submitData(entries: List<ChartEntry>) {
        this.entries = entries
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (entries.isEmpty()) return
        val left = paddingLeft.toFloat()
        val top = paddingTop.toFloat() + 28f
        val right = width - paddingRight.toFloat()
        val bottom = height - paddingBottom.toFloat() - 42f
        canvas.drawLine(left, bottom, right, bottom, axisPaint)
        val maxValue = max(entries.maxOf { it.value }, 1L)
        val slotWidth = (right - left) / entries.size
        val barWidth = slotWidth * 0.52f
        entries.forEachIndexed { index, item ->
            val centerX = left + slotWidth * index + slotWidth / 2
            val barHeight = ((item.value.toFloat() / maxValue) * (bottom - top)).coerceAtLeast(2f)
            barPaint.color = ContextCompat.getColor(context, item.colorRes ?: R.color.kitatrack_primary_green)
            canvas.drawRoundRect(
                RectF(centerX - barWidth / 2, bottom - barHeight, centerX + barWidth / 2, bottom),
                18f,
                18f,
                barPaint
            )
            canvas.drawText(item.displayValue, centerX, bottom - barHeight - 10f, valuePaint)
            canvas.drawText(item.label, centerX, height - paddingBottom.toFloat() - 8f, labelPaint)
        }
    }
}

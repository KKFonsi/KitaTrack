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

class HorizontalBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.kitatrack_secondary_text)
        textSize = 26f
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.kitatrack_divider)
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var entries: List<ChartEntry> = emptyList()

    fun submitData(entries: List<ChartEntry>) {
        this.entries = entries
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = paddingTop + paddingBottom + max(entries.size, 1) * 68
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(desiredHeight, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (entries.isEmpty()) return
        val maxValue = max(entries.maxOf { it.value }, 1L)
        entries.forEachIndexed { index, item ->
            val y = paddingTop + index * 68f
            canvas.drawText(item.label, paddingLeft.toFloat(), y + 24f, labelPaint)
            val trackTop = y + 34f
            val trackRight = width - paddingRight.toFloat()
            canvas.drawRoundRect(RectF(paddingLeft.toFloat(), trackTop, trackRight, trackTop + 18f), 12f, 12f, trackPaint)
            barPaint.color = ContextCompat.getColor(context, item.colorRes ?: R.color.kitatrack_primary_green)
            val progressRight = paddingLeft + ((trackRight - paddingLeft) * (item.value.toFloat() / maxValue))
            canvas.drawRoundRect(RectF(paddingLeft.toFloat(), trackTop, progressRight, trackTop + 18f), 12f, 12f, barPaint)
        }
    }
}

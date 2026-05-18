package com.example.kitatrack.ui.reports.chart

data class ChartEntry(
    val label: String,
    val value: Long,
    val displayValue: String = value.toString(),
    val colorRes: Int? = null
)

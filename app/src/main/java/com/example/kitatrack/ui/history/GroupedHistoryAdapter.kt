package com.example.kitatrack.ui.history

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.kitatrack.R
import com.example.kitatrack.util.Formatters
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private sealed interface HistoryListItem {
    data class Header(val dateMillis: Long) : HistoryListItem
    data class Transaction(val row: HistoryRow) : HistoryListItem
}

class GroupedHistoryAdapter(
    private val onClick: (HistoryRow) -> Unit,
    private val onLongClick: (HistoryRow) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var items: List<HistoryListItem> = emptyList()

    fun submitRows(rows: List<HistoryRow>) {
        val grouped = rows.groupBy { Formatters.date(it.item.transaction.occurredAt) }
        items = buildList {
            grouped.forEach { (_, dayRows) ->
                add(HistoryListItem.Header(dayRows.first().item.transaction.occurredAt))
                dayRows.forEach { add(HistoryListItem.Transaction(it)) }
            }
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int =
        when (items[position]) {
            is HistoryListItem.Header -> VIEW_TYPE_HEADER
            is HistoryListItem.Transaction -> VIEW_TYPE_TRANSACTION
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_history_date_header, parent, false)
            )
            else -> TransactionViewHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false)
            )
        }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is HistoryListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is HistoryListItem.Transaction -> (holder as TransactionViewHolder).bind(item.row)
        }
    }

    private class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(item: HistoryListItem.Header) {
            (itemView as TextView).text = historyHeader(item.dateMillis)
        }

        private fun historyHeader(millis: Long): String {
            val today = Calendar.getInstance()
            val date = Calendar.getInstance().apply { timeInMillis = millis }
            val short = SimpleDateFormat("MMM d", Locale.US).format(date.time)
            return if (today.get(Calendar.YEAR) == date.get(Calendar.YEAR) && today.get(Calendar.DAY_OF_YEAR) == date.get(Calendar.DAY_OF_YEAR)) {
                "Today, $short"
            } else {
                short
            }
        }
    }

    private inner class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title = itemView.findViewById<TextView>(R.id.transaction_title)
        private val amount = itemView.findViewById<TextView>(R.id.transaction_amount)
        private val icon = itemView.findViewById<TextView>(R.id.transaction_icon)
        private val meta = itemView.findViewById<TextView>(R.id.transaction_meta)
        private val note = itemView.findViewById<TextView>(R.id.transaction_note)
        private val balance = itemView.findViewById<TextView>(R.id.transaction_balance)

        fun bind(row: HistoryRow) {
            val tx = row.item.transaction
            val isIncome = tx.type == "INCOME"
            val accent = ContextCompat.getColor(itemView.context, if (isIncome) R.color.kitatrack_primary_green else R.color.kitatrack_expense_red)
            val iconBg = ContextCompat.getColor(itemView.context, if (isIncome) R.color.kitatrack_soft_mint else R.color.kitatrack_soft_danger_background)
            title.text = tx.description.ifBlank { row.item.categoryName ?: "Transaction" }
            amount.text = "${if (isIncome) "+" else "-"}${Formatters.peso(tx.amount)}"
            amount.setTextColor(accent)
            icon.text = if (isIncome) "+" else "-"
            icon.setTextColor(accent)
            icon.backgroundTintList = ColorStateList.valueOf(iconBg)
            meta.text = row.item.categoryName ?: if (isIncome) "Income" else "Expense"
            meta.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(itemView.context, R.color.kitatrack_elevated_card_background))
            note.visibility = if (tx.note.isNullOrBlank()) View.GONE else View.VISIBLE
            note.text = tx.note
            balance.text = "After: ${Formatters.peso(row.balanceAfter)}"
            itemView.setOnClickListener { onClick(row) }
            itemView.setOnLongClickListener {
                onLongClick(row)
                true
            }
        }
    }

    private companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_TRANSACTION = 1
    }
}

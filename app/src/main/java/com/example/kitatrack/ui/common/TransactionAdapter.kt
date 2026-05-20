package com.example.kitatrack.ui.common

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.kitatrack.R
import com.example.kitatrack.ui.history.HistoryRow
import com.example.kitatrack.util.Formatters

class TransactionAdapter(
    private val onClick: ((HistoryRow) -> Unit)? = null,
    private val onLongClick: ((HistoryRow) -> Unit)? = null
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {
    private var items: List<HistoryRow> = emptyList()

    fun submitList(newItems: List<HistoryRow>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size

    inner class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
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
            meta.text = "${if (isIncome) "Income" else "Expense"} | ${row.item.categoryName ?: "Uncategorized"} | ${Formatters.shortDate(tx.occurredAt)}"
            note.visibility = if (tx.note.isNullOrBlank()) View.GONE else View.VISIBLE
            note.text = tx.note
            balance.text = "Balance after: ${Formatters.peso(row.balanceAfter)}"
            itemView.setOnClickListener { onClick?.invoke(row) }
            itemView.setOnLongClickListener {
                onLongClick?.invoke(row)
                onLongClick != null
            }
        }
    }
}

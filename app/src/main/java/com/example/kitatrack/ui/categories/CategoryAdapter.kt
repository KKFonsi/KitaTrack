package com.example.kitatrack.ui.categories

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.kitatrack.R
import com.example.kitatrack.data.local.entity.CategoryEntity
import com.google.android.material.button.MaterialButton

class CategoryAdapter(
    private val onEdit: (CategoryEntity) -> Unit,
    private val onDelete: (CategoryEntity) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {
    private var items: List<CategoryEntity> = emptyList()

    fun submitList(newItems: List<CategoryEntity>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder =
        CategoryViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false))

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size

    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name = itemView.findViewById<TextView>(R.id.category_name)
        private val type = itemView.findViewById<TextView>(R.id.category_type)
        private val icon = itemView.findViewById<ImageView>(R.id.category_icon)
        private val edit = itemView.findViewById<MaterialButton>(R.id.edit_category_button)
        private val delete = itemView.findViewById<MaterialButton>(R.id.delete_category_button)

        fun bind(category: CategoryEntity) {
            name.text = category.name
            type.text = if (category.isDefault) "Default" else "Custom"
            icon.setImageResource(iconFor(category.name))
            edit.visibility = if (category.isDefault) View.GONE else View.VISIBLE
            delete.visibility = if (category.isDefault) View.GONE else View.VISIBLE
            edit.setOnClickListener { onEdit(category) }
            delete.setOnClickListener { onDelete(category) }
        }

        private fun iconFor(name: String): Int {
            val normalized = name.lowercase()
                .replace("&", "and")
                .replace("/", " ")
                .replace("-", " ")
                .replace("_", " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            return when {
                normalized.contains("food") || normalized.contains("drink") -> R.drawable.ic_expense_food_and_drinks
                normalized.contains("transport") || normalized.contains("gas") -> R.drawable.ic_expense_transportation
                normalized.contains("shopping") -> R.drawable.ic_expense_shopping
                normalized.contains("health") || normalized.contains("medical") -> R.drawable.ic_expense_health
                normalized.contains("debt") || normalized.contains("loan") -> R.drawable.ic_expense_loan
                normalized.contains("donation") || normalized.contains("donate") -> R.drawable.ic_expense_donation
                normalized.contains("gaming") || normalized.contains("game") -> R.drawable.ic_expense_gaming
                normalized.contains("rent") || normalized.contains("housing") || normalized.contains("house") -> R.drawable.ic_expense_bill
                normalized.contains("education") || normalized.contains("school") -> R.drawable.ic_expense_school
                normalized.contains("internet") -> R.drawable.ic_expense_internet
                normalized.contains("personal") || normalized.contains("care") -> R.drawable.ic_expense_personal_care
                normalized.contains("saving") -> R.drawable.ic_expense_savings
                normalized.contains("family") -> R.drawable.ic_expense_family
                normalized.contains("emergency") -> R.drawable.ic_expense_emergency
                normalized.contains("transfer") || normalized.contains("cash") -> R.drawable.ic_expense_cash_transfer
                normalized.contains("bill") || normalized.contains("subscription") -> R.drawable.ic_expense_bill
                else -> R.drawable.ic_expense_other
            }
        }
    }
}

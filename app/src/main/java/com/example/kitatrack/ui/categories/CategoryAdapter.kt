package com.example.kitatrack.ui.categories

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.kitatrack.R
import com.example.kitatrack.data.local.entity.CategoryEntity
import com.example.kitatrack.data.repository.CategoryRepository
import com.example.kitatrack.util.CategoryIconMapper
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
            val isIncomeSource = category.type == CategoryRepository.TYPE_INCOME_SOURCE
            name.text = category.name
            type.text = if (category.isDefault) "Default" else "Custom"
            icon.setImageResource(
                if (isIncomeSource) {
                    CategoryIconMapper.incomeIconFor(category.name)
                } else {
                    CategoryIconMapper.expenseIconFor(category.name)
                }
            )
            icon.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    itemView.context,
                    if (isIncomeSource) R.color.kitatrack_primary_green else R.color.kitatrack_secondary_text
                )
            )
            edit.visibility = if (category.isDefault) View.GONE else View.VISIBLE
            delete.visibility = if (category.isDefault) View.GONE else View.VISIBLE
            edit.setOnClickListener { onEdit(category) }
            delete.setOnClickListener { onDelete(category) }
        }
    }
}

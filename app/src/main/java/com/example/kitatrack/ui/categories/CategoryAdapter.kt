package com.example.kitatrack.ui.categories

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
        private val edit = itemView.findViewById<MaterialButton>(R.id.edit_category_button)
        private val delete = itemView.findViewById<MaterialButton>(R.id.delete_category_button)

        fun bind(category: CategoryEntity) {
            name.text = category.name
            type.text = if (category.isDefault) "Default category" else "Custom category"
            edit.visibility = if (category.isDefault) View.GONE else View.VISIBLE
            delete.visibility = if (category.isDefault) View.GONE else View.VISIBLE
            edit.setOnClickListener { onEdit(category) }
            delete.setOnClickListener { onDelete(category) }
        }
    }
}

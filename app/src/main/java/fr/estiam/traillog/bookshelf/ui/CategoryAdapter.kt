package fr.estiam.traillog.bookshelf.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import fr.estiam.traillog.R
import fr.estiam.traillog.bookshelf.model.CategorySummary
import fr.estiam.traillog.databinding.ItemCategoryBinding

/** Rows of the categories screen: name, book count, edit and delete buttons. */
class CategoryAdapter(
    private val onEdit: (CategorySummary) -> Unit,
    private val onDelete: (CategorySummary) -> Unit
) : ListAdapter<CategorySummary, CategoryAdapter.Holder>(Diff) {

    class Holder(val binding: ItemCategoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        val res = holder.itemView.resources
        holder.binding.categoryItemName.text = item.name
        holder.binding.categoryBookCount.text = res.getQuantityString(R.plurals.category_book_count, item.bookCount, item.bookCount)
        holder.binding.editCategoryButton.setOnClickListener { onEdit(item) }
        holder.binding.deleteCategoryButton.setOnClickListener { onDelete(item) }
    }

    private object Diff : DiffUtil.ItemCallback<CategorySummary>() {
        override fun areItemsTheSame(a: CategorySummary, b: CategorySummary) = a.id == b.id
        override fun areContentsTheSame(a: CategorySummary, b: CategorySummary) = a == b
    }
}

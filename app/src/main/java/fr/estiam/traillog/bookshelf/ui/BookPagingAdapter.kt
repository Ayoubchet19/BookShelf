package fr.estiam.traillog.bookshelf.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import fr.estiam.traillog.bookshelf.model.Book
import fr.estiam.traillog.databinding.ItemBookBinding

class BookPagingAdapter(private val onClick: (Book) -> Unit) :
    PagingDataAdapter<Book, BookPagingAdapter.Holder>(DIFF) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemBookBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) { getItem(position)?.let(holder::bind) }
    inner class Holder(private val binding: ItemBookBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(book: Book) {
            binding.title.text = book.title
            binding.author.text = book.author
            binding.category.text = book.categoryName
            binding.progress.text = "${book.progress}% · ${book.pagesRead}/${book.pageCount} pages"
            binding.favorite.text = if (book.isFavorite) "★" else "☆"
            binding.root.setOnClickListener { onClick(book) }
            binding.root.contentDescription = "${book.title}, ${book.author}, ${book.progress}%"
        }
    }
    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Book>() {
            override fun areItemsTheSame(a: Book, b: Book) = a.id == b.id
            override fun areContentsTheSame(a: Book, b: Book) = a == b
        }
    }
}

package fr.estiam.traillog.bookshelf.model

/** A category with the number of books that use it (categories screen). */
data class CategorySummary(val id: String, val name: String, val bookCount: Int)

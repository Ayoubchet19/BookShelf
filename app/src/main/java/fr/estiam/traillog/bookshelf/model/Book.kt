package fr.estiam.traillog.bookshelf.model

data class Book(
    val id: String,
    val title: String,
    val author: String,
    val categoryId: String,
    val categoryName: String = "",
    val pageCount: Int,
    val pagesRead: Int = 0,
    val isFavorite: Boolean = false,
    val isCompleted: Boolean = false,
    val notes: String = ""
) {
    val progress: Int
        get() = if (pageCount == 0) 0 else (pagesRead * 100 / pageCount).coerceIn(0, 100)

    fun isValid(): Boolean = id.isNotBlank() && title.isNotBlank() && author.isNotBlank() &&
        categoryId.isNotBlank() && pageCount > 0 && pagesRead in 0..pageCount
}

data class Category(val id: String, val name: String)
data class ReadingSession(
    val id: String,
    val bookId: String,
    val pagesRead: Int,
    val durationMinutes: Int,
    val startedAt: Long
)

data class UserPreferences(
    val sort: String = "TITLE",
    val darkMode: Boolean = false,
    val showCompleted: Boolean = true
)

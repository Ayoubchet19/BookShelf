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

/** Values stored in the "sort" preference. */
object SortOrder {
    const val TITLE = "TITLE"
    const val AUTHOR = "AUTHOR"
    /** Highest reading progress first. */
    const val PROGRESS = "PROGRESS"
    /** Order of the options on the settings screen. */
    val ALL = listOf(TITLE, AUTHOR, PROGRESS)
    /** Unknown or missing values (old versions, corrupted file) fall back to TITLE. */
    fun normalize(value: String?): String = if (value != null && value in ALL) value else TITLE
}

data class UserPreferences(
    val sort: String = SortOrder.TITLE,
    val darkMode: Boolean = false,
    val showCompleted: Boolean = true
)

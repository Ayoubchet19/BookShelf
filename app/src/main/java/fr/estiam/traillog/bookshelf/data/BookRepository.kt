package fr.estiam.traillog.bookshelf.data

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.LoadState
import androidx.paging.map
import androidx.room.withTransaction
import fr.estiam.traillog.bookshelf.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import java.util.UUID

class BookRepository(private val db: BookDatabase, private val context: Context) {
    val categories: Flow<List<Category>> = db.categories().observeAll().map { it.map { c -> Category(c.id, c.name) } }
    val completedCount = db.books().completedCount()
    val favoriteCount = db.books().favoriteCount()
    val pagesRead = db.books().pagesRead()
    val totalMinutes = db.sessions().totalMinutes()

    fun books(query: String, favorites: Boolean, completed: Boolean, sort: String): Flow<PagingData<Book>> =
        Pager(PagingConfig(pageSize = 20, enablePlaceholders = false)) {
            BookPagingSource(db.books().page(query, favorites, completed, sort))
        }.flow

    fun observeBook(id: String): Flow<Book?> = db.books().observeById(id).map { it?.toDomainBook() }
    fun sessions(bookId: String) = db.sessions().observeForBook(bookId)

    suspend fun save(book: Book) {
        require(book.isValid()) { "Book information is invalid" }
        db.books().upsert(book.toEntity())
    }

    suspend fun delete(id: String) = db.books().deleteById(id)
    suspend fun addCategory(name: String) {
        require(name.isNotBlank()) { "Category name is required" }
        db.categories().insert(CategoryEntity(name.trim().lowercase().replace(" ", "_"), name.trim()))
    }

    suspend fun addSession(bookId: String, pages: Int, minutes: Int) {
        require(pages > 0 && minutes > 0) { "Session values must be positive" }
        db.withTransaction {
            db.sessions().insert(ReadingSessionEntity(UUID.randomUUID().toString(), bookId, pages, minutes, System.currentTimeMillis()))
            db.books().findById(bookId)?.let { book ->
                val read = (book.pagesRead + pages).coerceAtMost(book.pageCount)
                db.books().upsert(book.copy(pagesRead = read, isCompleted = read == book.pageCount))
            }
        }
    }

    suspend fun seedIfNeeded() {
        if (db.books().count() > 0) return
        val json = JSONArray(context.assets.open("books.json").bufferedReader().use { it.readText() })
        db.withTransaction {
            val categoryNames = buildSet {
                for (i in 0 until json.length()) add(json.getJSONObject(i).getString("category"))
            }
            categoryNames.forEach { name -> db.categories().insert(CategoryEntity(name.lowercase().replace(" ", "_"), name)) }
            for (i in 0 until json.length()) {
                val item = json.getJSONObject(i)
                val categoryId = item.getString("category").lowercase().replace(" ", "_")
                val book = BookEntity(item.getString("id"), item.getString("title"), item.getString("author"),
                    categoryId, item.getInt("pageCount"), 0, false, false, item.optString("notes"))
                if (book.title.isNotBlank() && book.author.isNotBlank() && book.pageCount > 0) db.books().upsert(book)
            }
        }
    }

    private fun Book.toEntity() = BookEntity(id, title, author, categoryId, pageCount, pagesRead, isFavorite, isCompleted, notes)
}

private class BookPagingSource(private val source: PagingSource<Int, BookWithCategory>) : PagingSource<Int, Book>() {
    init { source.registerInvalidatedCallback { invalidate() } }
    override suspend fun load(params: LoadParams<Int>) = when (val result = source.load(params)) {
        is LoadResult.Page -> LoadResult.Page(result.data.map { it.toDomainBook() }, result.prevKey, result.nextKey, result.itemsBefore, result.itemsAfter)
        is LoadResult.Error -> LoadResult.Error(result.throwable)
        is LoadResult.Invalid -> LoadResult.Invalid()
    }
    override fun getRefreshKey(state: PagingState<Int, Book>) = state.anchorPosition
}

private fun BookWithCategory.toDomainBook() = Book(book.id, book.title, book.author, book.categoryId, category.name,
    book.pageCount, book.pagesRead, book.isFavorite, book.isCompleted, book.notes)

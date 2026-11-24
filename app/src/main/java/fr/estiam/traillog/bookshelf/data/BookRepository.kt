package fr.estiam.traillog.bookshelf.data

import android.content.Context
import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.LoadState
import androidx.paging.map
import androidx.room.withTransaction
import fr.estiam.traillog.bookshelf.domain.RawBookRecord
import fr.estiam.traillog.bookshelf.domain.SessionError
import fr.estiam.traillog.bookshelf.domain.SessionValidationException
import fr.estiam.traillog.bookshelf.domain.categoryIdOf
import fr.estiam.traillog.bookshelf.domain.computeProgress
import fr.estiam.traillog.bookshelf.domain.planImport
import fr.estiam.traillog.bookshelf.domain.remainingPages
import fr.estiam.traillog.bookshelf.domain.validateSession
import fr.estiam.traillog.bookshelf.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
// Book form & categories
import fr.estiam.traillog.bookshelf.domain.BookFormInput
import fr.estiam.traillog.bookshelf.domain.BookFormResult
import fr.estiam.traillog.bookshelf.domain.BookFormValidationException
import fr.estiam.traillog.bookshelf.domain.CategoryError
import fr.estiam.traillog.bookshelf.domain.CategoryValidationException
import fr.estiam.traillog.bookshelf.domain.checkCategoryDeletion
import fr.estiam.traillog.bookshelf.domain.newCategoryId
import fr.estiam.traillog.bookshelf.domain.normalizeCategoryName
import fr.estiam.traillog.bookshelf.domain.validateBookForm
import fr.estiam.traillog.bookshelf.domain.validateCategoryName
import kotlinx.coroutines.flow.first

class BookRepository(private val db: BookDatabase, private val context: Context) {
    val categories: Flow<List<Category>> = db.categories().observeAll().map { it.map { c -> Category(c.id, c.name) } }
    val completedCount = db.books().completedCount()
    val favoriteCount = db.books().favoriteCount()
    val pagesRead = db.books().pagesRead()
    val totalMinutes = db.sessions().totalMinutes()

    /**
     * Main catalogue, paged by 20. [hideCompleted] comes from the "Show completed books"
     * preference; [sort] is one of the SortOrder values.
     */
    fun books(query: String, favorites: Boolean, completed: Boolean, sort: String, hideCompleted: Boolean = false): Flow<PagingData<Book>> =
        Pager(PagingConfig(pageSize = 20, enablePlaceholders = false)) {
            BookPagingSource(db.books().page(query, favorites, completed, hideCompleted, sort))
        }.flow

    fun observeBook(id: String): Flow<Book?> = db.books().observeById(id).map { it?.toDomainBook() }
    /** Sessions of one book, newest first. */
    fun sessions(bookId: String): Flow<List<ReadingSession>> = db.sessions().observeForBook(bookId).map { it.map { s -> s.toDomain() } }

    /** Every book with its category name (statistics screen). */
    val allBooks: Flow<List<Book>> = db.books().observeAllWithCategory().map { it.map { b -> b.toDomainBook() } }

    /** Every reading session, newest first (statistics screen). */
    val allSessions: Flow<List<ReadingSession>> = db.sessions().observeAll().map { it.map { s -> s.toDomain() } }

    suspend fun save(book: Book) {
        require(book.isValid()) { "Book information is invalid" }
        db.books().upsert(book.toEntity())
    }

    suspend fun delete(id: String) = db.books().deleteById(id)
    suspend fun addCategory(name: String) {
        require(name.isNotBlank()) { "Category name is required" }
        db.categories().insert(CategoryEntity(categoryIdOf(name), name.trim()))
    }

    // ---------------------------------------------------------------- Categories CRUD

    /** Categories with their book count, live (categories screen). */
    val categorySummaries: Flow<List<CategorySummary>> =
        db.categories().observeWithBookCount().map { rows -> rows.map { CategorySummary(it.id, it.name, it.bookCount) } }

    /**
     * Creates a category after checking its name (required, 2..30 chars, no duplicate).
     * @throws CategoryValidationException when the name is rejected (nothing is written).
     * @return the id of the new category.
     */
    suspend fun createCategory(name: String): String = db.withTransaction {
        val existing = db.categories().listAll()
        validateCategoryName(name, existing.map { Category(it.id, it.name) })?.let { throw CategoryValidationException(it) }
        val id = newCategoryId(name, existing.map { it.id }.toSet())
        db.categories().insert(CategoryEntity(id, normalizeCategoryName(name)))
        id
    }

    /**
     * Renames a category. Its id is kept, so every book of the category follows the new name.
     * @throws CategoryValidationException when the category is missing or the name is rejected.
     */
    suspend fun renameCategory(id: String, newName: String) {
        db.withTransaction {
            val current = db.categories().findById(id) ?: throw CategoryValidationException(CategoryError.NOT_FOUND)
            val existing = db.categories().listAll().map { Category(it.id, it.name) }
            validateCategoryName(newName, existing, editingId = id)?.let { throw CategoryValidationException(it) }
            db.categories().update(current.copy(name = normalizeCategoryName(newName)))
        }
    }

    /**
     * Deletes a category only when no book uses it (the books table has an ON DELETE RESTRICT
     * foreign key: deleting a used category would otherwise crash with SQLiteConstraintException).
     * @throws CategoryValidationException NOT_FOUND, or IN_USE with the number of books.
     */
    suspend fun deleteCategory(id: String) {
        db.withTransaction {
            db.categories().findById(id) ?: throw CategoryValidationException(CategoryError.NOT_FOUND)
            val count = db.categories().countBooks(id)
            checkCategoryDeletion(count)?.let { throw CategoryValidationException(it, count) }
            db.categories().deleteById(id)
        }
    }

    // ---------------------------------------------------------------- Book form

    /**
     * Saves the add/edit book form in one transaction.
     * - The input is validated field by field (domain/BookForm.kt).
     * - On edit, the book is re-read from the database: favorite and reading progress are
     *   never overwritten by a stale copy, and the page count cannot go below the pages
     *   already read. Completion is recomputed for the new page count.
     * @param bookId null to create a new book.
     * @throws BookFormValidationException with one error per invalid field (nothing is written).
     * @return the id of the saved book.
     */
    suspend fun saveBookForm(bookId: String?, input: BookFormInput): String = db.withTransaction {
        val current = bookId?.let { db.books().findById(it) }
        val categoryIds = db.categories().listAll().map { it.id }.toSet()
        val form = when (val result = validateBookForm(input, current?.pagesRead ?: 0, categoryIds)) {
            is BookFormResult.Invalid -> throw BookFormValidationException(result.errors)
            is BookFormResult.Valid -> result.value
        }
        val id = current?.id ?: bookId ?: UUID.randomUUID().toString()
        val pagesRead = current?.pagesRead ?: 0
        db.books().upsert(
            BookEntity(
                id = id, title = form.title, author = form.author, categoryId = form.categoryId,
                pageCount = form.pageCount, pagesRead = pagesRead,
                isFavorite = current?.isFavorite ?: false,
                isCompleted = pagesRead >= form.pageCount,
                notes = form.notes
            )
        )
        id
    }

    /** One-shot read of a book (form pre-filling). */
    suspend fun findBook(id: String): Book? = db.books().observeById(id).first()?.toDomainBook()

    /**
     * Records a reading session and updates the book's progress in one transaction.
     * Rules: the book must exist, pages > 0, minutes > 0 and pages <= remaining pages.
     * @throws SessionValidationException when a rule is broken (nothing is written).
     * @return the id of the new session.
     */
    suspend fun addSession(bookId: String, pages: Int, minutes: Int, startedAt: Long = System.currentTimeMillis()): String =
        db.withTransaction {
            val book = db.books().findById(bookId) ?: throw SessionValidationException(SessionError.BOOK_NOT_FOUND)
            val existing = db.sessions().listForBook(bookId).map { it.toDomain() }
            checkSession(pages, minutes, book, existing)
            val session = ReadingSessionEntity(UUID.randomUUID().toString(), bookId, pages, minutes, startedAt)
            db.sessions().insert(session)
            recalculateProgress(book)
            session.id
        }

    /**
     * Changes the pages and minutes of an existing session (its date is kept) and
     * recomputes the book's progress in the same transaction. The page limit is the
     * book's remaining pages plus the pages this session already counted.
     * @throws SessionValidationException when a rule is broken (nothing is written).
     */
    suspend fun updateSession(sessionId: String, pages: Int, minutes: Int) {
        db.withTransaction {
            val session = db.sessions().findById(sessionId) ?: throw SessionValidationException(SessionError.SESSION_NOT_FOUND)
            val book = db.books().findById(session.bookId) ?: throw SessionValidationException(SessionError.BOOK_NOT_FOUND)
            val others = db.sessions().listForBook(book.id).filter { it.id != sessionId }.map { it.toDomain() }
            checkSession(pages, minutes, book, others)
            db.sessions().update(session.copy(pagesRead = pages, durationMinutes = minutes))
            recalculateProgress(book)
        }
    }

    /**
     * Deletes a session and recomputes the book's progress in the same transaction.
     * A completed book goes back to "in progress" when its pages are no longer all covered.
     * @throws SessionValidationException when the session does not exist.
     */
    suspend fun deleteSession(sessionId: String) {
        db.withTransaction {
            val session = db.sessions().findById(sessionId) ?: throw SessionValidationException(SessionError.SESSION_NOT_FOUND)
            db.sessions().deleteById(sessionId)
            db.books().findById(session.bookId)?.let { recalculateProgress(it) }
        }
    }

    private fun checkSession(pages: Int, minutes: Int, book: BookEntity, otherSessions: List<ReadingSession>) {
        val alreadyRead = computeProgress(book.pageCount, otherSessions).pagesRead
        validateSession(pages, minutes, book.pageCount, alreadyRead)?.let { error ->
            throw SessionValidationException(error, remainingPages(book.pageCount, alreadyRead))
        }
    }

    /** Must be called inside db.withTransaction: pagesRead = capped sum of the book's sessions. */
    private suspend fun recalculateProgress(book: BookEntity) {
        val progress = computeProgress(book.pageCount, db.sessions().listForBook(book.id).map { it.toDomain() })
        db.books().upsert(book.copy(pagesRead = progress.pagesRead, isCompleted = progress.isCompleted))
    }

    private val seedMutex = Mutex()

    /**
     * Imports assets/books.json the first time the app runs, and never again ([flag] is persisted).
     * - Every record is validated first (domain/BookImport.kt); invalid ones are skipped and logged.
     * - Rows are inserted with INSERT OR IGNORE, so an existing book is never overwritten.
     * - Deleting every book later does not bring the sample books back.
     * The mutex stops two concurrent calls (activity restarted quickly) from importing twice.
     * @return the number of books inserted.
     */
    suspend fun seedIfNeeded(flag: InitialImportFlag): Int = seedMutex.withLock {
        if (flag.isDone()) return@withLock 0
        if (db.books().count() > 0) {
            // Database created by a version without the flag: the user already has data.
            flag.markDone()
            return@withLock 0
        }
        val records = readBundledRecords() ?: return@withLock 0
        val plan = planImport(records, db.books().allIds().toSet())
        plan.rejected.forEach { Log.w(TAG, "$BOOKS_ASSET record #${it.index} (id=${it.id}) skipped: ${it.reason}") }
        db.withTransaction {
            plan.categories.forEach { (id, name) -> db.categories().insert(CategoryEntity(id, name)) }
            plan.accepted.forEach { r ->
                db.books().insertIgnore(BookEntity(r.id, r.title, r.author, r.categoryId, r.pageCount, 0, false, false, r.notes))
            }
        }
        flag.markDone()
        Log.i(TAG, "Initial import: ${plan.accepted.size} books inserted, ${plan.rejected.size} skipped")
        plan.accepted.size
    }

    /** Reads the bundled file. Returns null (and logs) when it is missing or is not a JSON array. */
    private suspend fun readBundledRecords(): List<RawBookRecord>? = withContext(Dispatchers.IO) {
        try {
            val json = JSONArray(context.assets.open(BOOKS_ASSET).bufferedReader().use { it.readText() })
            (0 until json.length()).map { i -> json.optJSONObject(i).toRawRecord() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Cannot read $BOOKS_ASSET, initial import skipped", e)
            null
        }
    }

    companion object {
        private const val TAG = "BookRepository"
        const val BOOKS_ASSET = "books.json"
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

/** Lenient JSON -> RawBookRecord: a wrong type becomes null, and planImport() then rejects the record. */
private fun JSONObject?.toRawRecord(): RawBookRecord {
    if (this == null) return RawBookRecord(null, null, null, null, null)
    fun text(key: String): String? = if (has(key) && !isNull(key)) optString(key) else null
    val pages = (opt("pageCount") as? Number)?.takeIf { it.toDouble() % 1.0 == 0.0 }?.toInt()
    return RawBookRecord(text("id"), text("title"), text("author"), text("category"), pages, text("notes"))
}

private fun ReadingSessionEntity.toDomain() = ReadingSession(id, bookId, pagesRead, durationMinutes, startedAt)

private fun BookWithCategory.toDomainBook() = Book(book.id, book.title, book.author, book.categoryId, category.name,
    book.pageCount, book.pagesRead, book.isFavorite, book.isCompleted, book.notes)

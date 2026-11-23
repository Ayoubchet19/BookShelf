package fr.estiam.traillog.bookshelf.data

import androidx.paging.PagingSource
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "categories")
data class CategoryEntity(@PrimaryKey val id: String, val name: String)

@Entity(
    tableName = "books",
    foreignKeys = [ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("categoryId")]
)
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val categoryId: String,
    val pageCount: Int,
    val pagesRead: Int,
    val isFavorite: Boolean,
    val isCompleted: Boolean,
    val notes: String
)

@Entity(
    tableName = "reading_sessions",
    foreignKeys = [ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("bookId")]
)
data class ReadingSessionEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val pagesRead: Int,
    val durationMinutes: Int,
    val startedAt: Long
)

data class BookWithCategory(
    @Embedded val book: BookEntity,
    @Relation(parentColumn = "categoryId", entityColumn = "id") val category: CategoryEntity
)

@Dao
interface BookDao {
    @Transaction
    @Query("""SELECT * FROM books
        WHERE (:query = '' OR title LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%')
        AND (:favoritesOnly = 0 OR isFavorite = 1)
        AND (:completedOnly = 0 OR isCompleted = 1)
        ORDER BY CASE WHEN :sort = 'AUTHOR' THEN author ELSE title END COLLATE NOCASE""")
    fun page(query: String, favoritesOnly: Boolean, completedOnly: Boolean, sort: String): PagingSource<Int, BookWithCategory>

    @Transaction
    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<BookWithCategory?>

    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): BookEntity?

    @Upsert suspend fun upsert(book: BookEntity)
    @Query("DELETE FROM books WHERE id = :id") suspend fun deleteById(id: String)
    @Query("SELECT COUNT(*) FROM books") suspend fun count(): Int
    @Query("SELECT COUNT(*) FROM books WHERE isCompleted = 1") fun completedCount(): Flow<Int>
    @Query("SELECT COUNT(*) FROM books WHERE isFavorite = 1") fun favoriteCount(): Flow<Int>
    @Query("SELECT COALESCE(SUM(pagesRead), 0) FROM books") fun pagesRead(): Flow<Int>

    /** All books with their category, used by the statistics screen. */
    @Transaction
    @Query("SELECT * FROM books ORDER BY title COLLATE NOCASE")
    fun observeAllWithCategory(): Flow<List<BookWithCategory>>
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE") fun observeAll(): Flow<List<CategoryEntity>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(category: CategoryEntity)
    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1") suspend fun findById(id: String): CategoryEntity?
}

@Dao
interface ReadingSessionDao {
    @Query("SELECT * FROM reading_sessions WHERE bookId = :bookId ORDER BY startedAt DESC")
    fun observeForBook(bookId: String): Flow<List<ReadingSessionEntity>>
    @Insert suspend fun insert(session: ReadingSessionEntity)
    @Update suspend fun update(session: ReadingSessionEntity)
    @Query("DELETE FROM reading_sessions WHERE id = :id") suspend fun deleteById(id: String)
    @Query("SELECT * FROM reading_sessions WHERE id = :id LIMIT 1") suspend fun findById(id: String): ReadingSessionEntity?
    /** Snapshot of a book's sessions, read inside transactions to recompute progress. */
    @Query("SELECT * FROM reading_sessions WHERE bookId = :bookId") suspend fun listForBook(bookId: String): List<ReadingSessionEntity>
    /** Every session, newest first, used by the statistics screen (speed, streak, minutes per category). */
    @Query("SELECT * FROM reading_sessions ORDER BY startedAt DESC") fun observeAll(): Flow<List<ReadingSessionEntity>>
    @Query("SELECT COALESCE(SUM(durationMinutes), 0) FROM reading_sessions") fun totalMinutes(): Flow<Int>
}

@Database(entities = [BookEntity::class, CategoryEntity::class, ReadingSessionEntity::class], version = 1, exportSchema = false)
abstract class BookDatabase : RoomDatabase() {
    abstract fun books(): BookDao
    abstract fun categories(): CategoryDao
    abstract fun sessions(): ReadingSessionDao
}

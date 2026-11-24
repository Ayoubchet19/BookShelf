package fr.estiam.traillog.bookshelf

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.estiam.traillog.bookshelf.data.BookDatabase
import fr.estiam.traillog.bookshelf.data.BookRepository
import fr.estiam.traillog.bookshelf.domain.BookFormError
import fr.estiam.traillog.bookshelf.domain.BookFormInput
import fr.estiam.traillog.bookshelf.domain.BookFormValidationException
import fr.estiam.traillog.bookshelf.domain.CategoryError
import fr.estiam.traillog.bookshelf.domain.CategoryValidationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Categories CRUD and book-form saving against an in-memory Room database. */
@RunWith(AndroidJUnit4::class)
class CategoryAndFormRepositoryTest {
    private lateinit var db: BookDatabase
    private lateinit var repository: BookRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, BookDatabase::class.java).build()
        repository = BookRepository(db, context)
    }

    @After fun tearDown() = db.close()

    private fun form(pages: String = "200", category: String? = "fantasy") =
        BookFormInput("The Hobbit", "Tolkien", pages, category, "")

    @Test fun createRenameAndDeleteCategory() = runBlocking {
        val id = repository.createCategory("  Fantasy ")
        assertEquals("fantasy", id)
        repository.renameCategory(id, "High Fantasy")
        val summaries = repository.categorySummaries.first()
        assertEquals(listOf("High Fantasy"), summaries.map { it.name })
        assertEquals(id, summaries.single().id) // id kept on rename
        repository.deleteCategory(id)
        assertTrue(repository.categorySummaries.first().isEmpty())
    }

    @Test fun duplicateCategoryIsRejected() = runBlocking {
        repository.createCategory("Fantasy")
        val e = assertThrows(CategoryValidationException::class.java) { runBlocking { repository.createCategory("FANTASY") } }
        assertEquals(CategoryError.DUPLICATE_NAME, e.error)
        assertEquals(1, repository.categorySummaries.first().size)
    }

    @Test fun categoryUsedByABookCannotBeDeleted() = runBlocking {
        val id = repository.createCategory("Fantasy")
        repository.saveBookForm(null, form(category = id))
        assertEquals(1, repository.categorySummaries.first().single().bookCount)
        val e = assertThrows(CategoryValidationException::class.java) { runBlocking { repository.deleteCategory(id) } }
        assertEquals(CategoryError.IN_USE, e.error)
        assertEquals(1, e.bookCount)
    }

    @Test fun renamedCategoryIsSeenByItsBooks() = runBlocking {
        val id = repository.createCategory("Fantasy")
        val bookId = repository.saveBookForm(null, form(category = id))
        repository.renameCategory(id, "Epic Fantasy")
        assertEquals("Epic Fantasy", repository.findBook(bookId)!!.categoryName)
    }

    @Test fun editingABookKeepsProgressAndFavorite() = runBlocking {
        val cat = repository.createCategory("Fantasy")
        val bookId = repository.saveBookForm(null, form(pages = "100", category = cat))
        repository.addSession(bookId, 60, 30)
        repository.save(repository.findBook(bookId)!!.copy(isFavorite = true))

        repository.saveBookForm(bookId, form(pages = "60", category = cat))
        val book = repository.findBook(bookId)!!
        assertEquals(60, book.pagesRead)
        assertTrue(book.isFavorite)
        assertTrue(book.isCompleted) // 60/60 pages: completion recomputed for the new page count

        val e = assertThrows(BookFormValidationException::class.java) {
            runBlocking { repository.saveBookForm(bookId, form(pages = "59", category = cat)) }
        }
        assertEquals(BookFormError.PAGES_BELOW_PAGES_READ, e.errors.pageCount)
    }

    @Test fun unknownCategoryIsRejectedAndNothingIsWritten() = runBlocking {
        val e = assertThrows(BookFormValidationException::class.java) {
            runBlocking { repository.saveBookForm(null, form(category = "missing")) }
        }
        assertEquals(BookFormError.CATEGORY_NOT_FOUND, e.errors.category)
        assertTrue(repository.allBooks.first().isEmpty())
    }
}

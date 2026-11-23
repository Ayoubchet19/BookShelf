package fr.estiam.traillog.bookshelf

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.estiam.traillog.bookshelf.data.BookDatabase
import fr.estiam.traillog.bookshelf.data.BookRepository
import fr.estiam.traillog.bookshelf.domain.SessionError
import fr.estiam.traillog.bookshelf.domain.SessionValidationException
import fr.estiam.traillog.bookshelf.model.Book
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Repository rules against a real (in-memory) Room database: validation and the
 * transactional recalculation of pagesRead / isCompleted on add, update and delete.
 */
@RunWith(AndroidJUnit4::class)
class BookRepositorySessionTest {
    private lateinit var db: BookDatabase
    private lateinit var repository: BookRepository

    @Before fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, BookDatabase::class.java).build()
        repository = BookRepository(db, context)
        repository.addCategory("Tests")
        repository.save(Book("b1", "Title", "Author", "tests", pageCount = 100))
    }

    @After fun tearDown() = db.close()

    private suspend fun book() = repository.observeBook("b1").first()!!

    private fun assertRejected(expected: SessionError, block: suspend () -> Unit) = runBlocking {
        try { block(); fail("Expected $expected") } catch (e: SessionValidationException) { assertEquals(expected, e.error) }
    }

    @Test fun lastSessionCompletesBookAndDeletingItReopensIt() = runBlocking {
        repository.addSession("b1", 60, 30)
        val last = repository.addSession("b1", 40, 20)
        assertEquals(100, book().pagesRead)
        assertTrue(book().isCompleted)

        repository.deleteSession(last)
        assertEquals(60, book().pagesRead)
        assertFalse(book().isCompleted)
        assertEquals(1, repository.sessions("b1").first().size)
    }

    @Test fun updateRecalculatesProgressAndUsesOwnPagesInTheLimit() = runBlocking {
        repository.addSession("b1", 50, 30)
        val id = repository.addSession("b1", 30, 20)
        // 50 pages are covered by the other session, so this one may go up to 50.
        repository.updateSession(id, 50, 25)
        assertEquals(100, book().pagesRead)
        assertTrue(book().isCompleted)
        repository.updateSession(id, 10, 25)
        assertEquals(60, book().pagesRead)
        assertFalse(book().isCompleted)
    }

    @Test fun invalidSessionsAreRejectedAndNothingIsWritten() {
        assertRejected(SessionError.PAGES_NOT_POSITIVE) { repository.addSession("b1", 0, 10) }
        assertRejected(SessionError.MINUTES_NOT_POSITIVE) { repository.addSession("b1", 10, 0) }
        assertRejected(SessionError.PAGES_EXCEED_REMAINING) { repository.addSession("b1", 101, 10) }
        assertRejected(SessionError.BOOK_NOT_FOUND) { repository.addSession("missing", 10, 10) }
        assertRejected(SessionError.SESSION_NOT_FOUND) { repository.deleteSession("missing") }
        runBlocking {
            assertTrue(repository.sessions("b1").first().isEmpty())
            assertEquals(0, book().pagesRead)
        }
    }
}

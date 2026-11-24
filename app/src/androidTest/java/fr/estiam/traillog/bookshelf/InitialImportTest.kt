package fr.estiam.traillog.bookshelf

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.estiam.traillog.bookshelf.data.BookDatabase
import fr.estiam.traillog.bookshelf.data.BookRepository
import fr.estiam.traillog.bookshelf.data.InitialImportFlag
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Initial import of the real assets/books.json into an in-memory Room database:
 * at least 20 valid books, imported once, user changes never overwritten.
 */
@RunWith(AndroidJUnit4::class)
class InitialImportTest {
    private lateinit var db: BookDatabase
    private lateinit var repository: BookRepository
    private val flag = FakeFlag()

    /** In-memory stand-in for the DataStore flag. */
    private class FakeFlag : InitialImportFlag {
        var done = false
        override suspend fun isDone() = done
        override suspend fun markDone() { done = true }
    }

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, BookDatabase::class.java).build()
        repository = BookRepository(db, context)
    }

    @After fun tearDown() = db.close()

    @Test fun firstLaunchImportsAtLeastTwentyBooks() = runBlocking {
        val inserted = repository.seedIfNeeded(flag)
        assertTrue("only $inserted books imported", inserted >= 20)
        assertEquals(inserted, db.books().count())
        assertTrue(flag.done)
    }

    @Test fun secondCallImportsNothing() = runBlocking {
        val first = repository.seedIfNeeded(flag)
        assertEquals(0, repository.seedIfNeeded(flag))
        assertEquals(first, db.books().count())
    }

    @Test fun userChangesAreKeptEvenIfTheFlagIsLost() = runBlocking {
        repository.seedIfNeeded(flag)
        val book = db.books().findById("book-01")!!
        db.books().upsert(book.copy(title = "My edited title", isFavorite = true))
        flag.done = false // e.g. DataStore file cleared
        assertEquals(0, repository.seedIfNeeded(flag))
        assertEquals("My edited title", db.books().findById("book-01")!!.title)
        assertTrue(flag.done)
    }

    @Test fun deletingEveryBookDoesNotBringTheSamplesBack() = runBlocking {
        repository.seedIfNeeded(flag)
        db.books().allIds().forEach { db.books().deleteById(it) }
        assertEquals(0, repository.seedIfNeeded(flag))
        assertEquals(0, db.books().count())
    }
}

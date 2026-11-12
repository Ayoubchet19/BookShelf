package fr.estiam.traillog.bookshelf

import fr.estiam.traillog.bookshelf.model.Book
import org.junit.Assert.*
import org.junit.Test

class BookTest {
    private fun book(pagesRead: Int = 0) = Book("id", "Title", "Author", "category", pageCount = 100, pagesRead = pagesRead)
    @Test fun validBookIsAccepted() = assertTrue(book().isValid())
    @Test fun blankTitleIsRejected() = assertFalse(book().copy(title = " ").isValid())
    @Test fun blankAuthorIsRejected() = assertFalse(book().copy(author = "").isValid())
    @Test fun zeroPagesAreRejected() = assertFalse(book().copy(pageCount = 0).isValid())
    @Test fun negativeProgressIsRejected() = assertFalse(book(-1).isValid())
    @Test fun progressIsCalculatedAsPercentage() = assertEquals(40, book(40).progress)
    @Test fun progressCannotExceedOneHundred() = assertEquals(100, book(140).progress)
    @Test fun incompleteBookHasFalseCompletion() = assertFalse(book(99).isCompleted)
    @Test fun completedBookCanBeMarkedComplete() = assertTrue(book(100).copy(isCompleted = true).isCompleted)
    @Test fun favoriteStateIsPreserved() = assertTrue(book().copy(isFavorite = true).isFavorite)
}

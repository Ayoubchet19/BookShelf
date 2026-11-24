package fr.estiam.traillog.bookshelf

import fr.estiam.traillog.bookshelf.domain.*
import org.junit.Assert.*
import org.junit.Test

/** Rules of the add/edit book form (domain/BookForm.kt). */
class BookFormTest {
    private fun input(
        title: String = "Dune",
        author: String = "Frank Herbert",
        pages: String = "412",
        category: String? = "science_fiction",
        notes: String = ""
    ) = BookFormInput(title, author, pages, category, notes)

    private fun errorsOf(result: BookFormResult) = (result as BookFormResult.Invalid).errors

    @Test fun validFormReturnsCleanedValues() {
        val result = validateBookForm(input(title = "  The   Hobbit ", author = " J.R.R.  Tolkien", pages = " 310 ", notes = " nice \n"))
        val value = (result as BookFormResult.Valid).value
        assertEquals("The Hobbit", value.title)
        assertEquals("J.R.R. Tolkien", value.author)
        assertEquals(310, value.pageCount)
        assertEquals("nice", value.notes)
    }

    @Test fun emptyFormReportsEveryRequiredFieldAtOnce() {
        val errors = errorsOf(validateBookForm(input(title = " ", author = "", pages = "", category = null)))
        assertEquals(BookFormError.TITLE_REQUIRED, errors.title)
        assertEquals(BookFormError.AUTHOR_REQUIRED, errors.author)
        assertEquals(BookFormError.PAGES_REQUIRED, errors.pageCount)
        assertEquals(BookFormError.CATEGORY_REQUIRED, errors.category)
        assertNull(errors.notes)
        assertFalse(errors.isValid)
    }

    @Test fun nonNumericPageCountIsRejected() =
        assertEquals(BookFormError.PAGES_NOT_A_NUMBER, errorsOf(validateBookForm(input(pages = "12a"))).pageCount)

    @Test fun pageCountOverflowIsNotANumber() =
        assertEquals(BookFormError.PAGES_NOT_A_NUMBER, errorsOf(validateBookForm(input(pages = "99999999999"))).pageCount)

    @Test fun zeroAndTooManyPagesAreOutOfRange() {
        assertEquals(BookFormError.PAGES_OUT_OF_RANGE, errorsOf(validateBookForm(input(pages = "0"))).pageCount)
        assertEquals(BookFormError.PAGES_OUT_OF_RANGE, errorsOf(validateBookForm(input(pages = "-5"))).pageCount)
        assertEquals(BookFormError.PAGES_OUT_OF_RANGE, errorsOf(validateBookForm(input(pages = "${PAGE_COUNT_MAX + 1}"))).pageCount)
        assertTrue(validateBookForm(input(pages = "$PAGE_COUNT_MAX")) is BookFormResult.Valid)
    }

    @Test fun pageCountCannotGoBelowPagesAlreadyRead() {
        assertEquals(BookFormError.PAGES_BELOW_PAGES_READ,
            errorsOf(validateBookForm(input(pages = "99"), pagesAlreadyRead = 100)).pageCount)
        // Equal is allowed: the book simply becomes completed.
        assertTrue(validateBookForm(input(pages = "100"), pagesAlreadyRead = 100) is BookFormResult.Valid)
    }

    @Test fun tooLongFieldsAreRejected() {
        val errors = errorsOf(validateBookForm(input(
            title = "t".repeat(TITLE_MAX_LENGTH + 1),
            author = "a".repeat(AUTHOR_MAX_LENGTH + 1),
            notes = "n".repeat(NOTES_MAX_LENGTH + 1))))
        assertEquals(BookFormError.TITLE_TOO_LONG, errors.title)
        assertEquals(BookFormError.AUTHOR_TOO_LONG, errors.author)
        assertEquals(BookFormError.NOTES_TOO_LONG, errors.notes)
        assertNull(errors.pageCount)
    }

    @Test fun categoryMustStillExist() {
        val ids = setOf("fantasy", "history")
        assertEquals(BookFormError.CATEGORY_NOT_FOUND,
            errorsOf(validateBookForm(input(category = "science_fiction"), existingCategoryIds = ids)).category)
        assertTrue(validateBookForm(input(category = "fantasy"), existingCategoryIds = ids) is BookFormResult.Valid)
    }

    @Test fun notesAreOptional() = assertTrue(validateBookForm(input(notes = "   ")) is BookFormResult.Valid)
}

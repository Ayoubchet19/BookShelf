package fr.estiam.traillog.bookshelf

import fr.estiam.traillog.bookshelf.domain.ImportRejection
import fr.estiam.traillog.bookshelf.domain.MAX_PAGE_COUNT
import fr.estiam.traillog.bookshelf.domain.RawBookRecord
import fr.estiam.traillog.bookshelf.domain.categoryIdOf
import fr.estiam.traillog.bookshelf.domain.planImport
import org.junit.Assert.*
import org.junit.Test

/** Validation of the books.json records before the initial import (domain/BookImport.kt). */
class BookImportTest {
    private fun raw(
        id: String? = "b1", title: String? = "Dune", author: String? = "Frank Herbert",
        category: String? = "Science Fiction", pages: Int? = 688, notes: String? = null
    ) = RawBookRecord(id, title, author, category, pages, notes)

    private fun reasonFor(record: RawBookRecord) = planImport(listOf(record)).rejected.single().reason

    @Test fun validRecordIsAcceptedWithCategoryId() {
        val plan = planImport(listOf(raw()))
        assertTrue(plan.rejected.isEmpty())
        val book = plan.accepted.single()
        assertEquals("science_fiction", book.categoryId)
        assertEquals("Science Fiction", book.categoryName)
        assertEquals("", book.notes)
    }

    @Test fun textFieldsAreTrimmed() {
        val book = planImport(listOf(raw(id = " b1 ", title = "  Dune ", category = " Fantasy "))).accepted.single()
        assertEquals("b1", book.id)
        assertEquals("Dune", book.title)
        assertEquals("fantasy", book.categoryId)
    }

    @Test fun missingOrBlankIdIsRejected() {
        assertEquals(ImportRejection.MISSING_ID, reasonFor(raw(id = null)))
        assertEquals(ImportRejection.MISSING_ID, reasonFor(raw(id = "  ")))
    }

    @Test fun blankTitleAuthorOrCategoryIsRejected() {
        assertEquals(ImportRejection.BLANK_TITLE, reasonFor(raw(title = " ")))
        assertEquals(ImportRejection.BLANK_AUTHOR, reasonFor(raw(author = null)))
        assertEquals(ImportRejection.BLANK_CATEGORY, reasonFor(raw(category = "")))
    }

    @Test fun pageCountMustBeBetweenOneAndTheMaximum() {
        assertEquals(ImportRejection.INVALID_PAGE_COUNT, reasonFor(raw(pages = 0)))
        assertEquals(ImportRejection.INVALID_PAGE_COUNT, reasonFor(raw(pages = -5)))
        assertEquals(ImportRejection.INVALID_PAGE_COUNT, reasonFor(raw(pages = null)))
        assertEquals(ImportRejection.INVALID_PAGE_COUNT, reasonFor(raw(pages = MAX_PAGE_COUNT + 1)))
        assertEquals(1, planImport(listOf(raw(pages = MAX_PAGE_COUNT))).accepted.size)
    }

    @Test fun duplicateIdInTheFileKeepsTheFirstRecord() {
        val plan = planImport(listOf(raw(title = "First"), raw(title = "Second")))
        assertEquals("First", plan.accepted.single().title)
        assertEquals(ImportRejection.DUPLICATE_ID, plan.rejected.single().reason)
        assertEquals(1, plan.rejected.single().index)
    }

    @Test fun invalidFirstDuplicateDoesNotBlockAValidSecondOne() {
        val plan = planImport(listOf(raw(title = ""), raw(title = "Valid")))
        assertEquals("Valid", plan.accepted.single().title)
        assertEquals(ImportRejection.BLANK_TITLE, plan.rejected.single().reason)
    }

    @Test fun bookAlreadyInDatabaseIsNeverOverwritten() {
        val plan = planImport(listOf(raw(id = "b1"), raw(id = "b2")), existingIds = setOf("b1"))
        assertEquals(listOf("b2"), plan.accepted.map { it.id })
        assertEquals(ImportRejection.ALREADY_IN_DATABASE, plan.rejected.single().reason)
    }

    @Test fun categoriesAreCreatedOncePerId() {
        val plan = planImport(listOf(raw(id = "a", category = "Fantasy"), raw(id = "b", category = "Fantasy"), raw(id = "c", category = "History")))
        assertEquals(mapOf("fantasy" to "Fantasy", "history" to "History"), plan.categories)
    }

    @Test fun rejectedRecordsKeepTheirPositionInTheFile() {
        val plan = planImport(listOf(raw(id = "a"), raw(id = "b", pages = 0), raw(id = "c"), raw(id = null)))
        assertEquals(listOf(1, 3), plan.rejected.map { it.index })
        assertEquals(listOf("a", "c"), plan.accepted.map { it.id })
    }

    @Test fun categoryIdMatchesTheRepositoryRule() = assertEquals("personal_growth", categoryIdOf("  Personal Growth "))
}

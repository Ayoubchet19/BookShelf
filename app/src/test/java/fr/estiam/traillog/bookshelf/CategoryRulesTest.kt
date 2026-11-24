package fr.estiam.traillog.bookshelf

import fr.estiam.traillog.bookshelf.domain.*
import fr.estiam.traillog.bookshelf.model.Category
import org.junit.Assert.*
import org.junit.Test

/** Rules of the categories CRUD (domain/CategoryRules.kt). */
class CategoryRulesTest {
    private val existing = listOf(Category("fantasy", "Fantasy"), Category("science_fiction", "Science Fiction"))

    @Test fun nameIsNormalized() = assertEquals("Science Fiction", normalizeCategoryName("  Science    Fiction "))

    @Test fun idIsDerivedFromTheName() = assertEquals("science_fiction", categoryIdFor(" Science  Fiction"))

    @Test fun validNewNameIsAccepted() = assertNull(validateCategoryName("History", existing))

    @Test fun blankShortAndLongNamesAreRejected() {
        assertEquals(CategoryError.NAME_REQUIRED, validateCategoryName("   ", existing))
        assertEquals(CategoryError.NAME_TOO_SHORT, validateCategoryName(" a ", existing))
        assertEquals(CategoryError.NAME_TOO_LONG, validateCategoryName("x".repeat(CATEGORY_NAME_MAX_LENGTH + 1), existing))
        assertNull(validateCategoryName("x".repeat(CATEGORY_NAME_MAX_LENGTH), existing))
    }

    @Test fun duplicateIsDetectedIgnoringCaseAndSpaces() =
        assertEquals(CategoryError.DUPLICATE_NAME, validateCategoryName("  science   FICTION ", existing))

    @Test fun renamingKeepsItsOwnNameAllowed() {
        // Changing only the case of its own name is not a duplicate...
        assertNull(validateCategoryName("FANTASY", existing, editingId = "fantasy"))
        // ...but taking another category's name is.
        assertEquals(CategoryError.DUPLICATE_NAME, validateCategoryName("Fantasy", existing, editingId = "science_fiction"))
    }

    @Test fun newIdAvoidsIdsKeptByRenamedCategories() {
        // "Fantasy" was renamed but still owns the id "fantasy".
        assertEquals("fantasy", newCategoryId("Fantasy", emptySet()))
        assertEquals("fantasy_2", newCategoryId("Fantasy", setOf("fantasy")))
        assertEquals("fantasy_3", newCategoryId("Fantasy", setOf("fantasy", "fantasy_2")))
    }

    @Test fun categoryUsedByBooksCannotBeDeleted() {
        assertEquals(CategoryError.IN_USE, checkCategoryDeletion(bookCount = 3))
        assertNull(checkCategoryDeletion(bookCount = 0))
    }
}

package fr.estiam.traillog.bookshelf

import fr.estiam.traillog.bookshelf.domain.catalogueFilter
import fr.estiam.traillog.bookshelf.model.SortOrder
import fr.estiam.traillog.bookshelf.model.UserPreferences
import org.junit.Assert.*
import org.junit.Test

/** How the DataStore preferences change the catalogue query (domain/CatalogueFilter.kt). */
class CatalogueFilterTest {
    private val defaults = UserPreferences()

    @Test fun defaultPreferencesShowEverythingSortedByTitle() {
        val filter = catalogueFilter("", favoritesOnly = false, completedOnly = false, preferences = defaults)
        assertFalse(filter.hideCompleted)
        assertEquals(SortOrder.TITLE, filter.sort)
        assertFalse(filter.isNarrowed)
    }

    @Test fun showCompletedOffHidesCompletedBooks() {
        val filter = catalogueFilter("", false, false, defaults.copy(showCompleted = false))
        assertTrue(filter.hideCompleted)
        assertTrue(filter.isNarrowed)
    }

    @Test fun completedFilterIsIgnoredWhenCompletedBooksAreHidden() {
        val filter = catalogueFilter("", false, completedOnly = true, preferences = defaults.copy(showCompleted = false))
        assertFalse(filter.completedOnly)
        assertTrue(catalogueFilter("", false, true, defaults).completedOnly)
    }

    @Test fun storedSortOrderIsUsed() {
        assertEquals(SortOrder.PROGRESS, catalogueFilter("", false, false, defaults.copy(sort = SortOrder.PROGRESS)).sort)
        assertEquals(SortOrder.AUTHOR, catalogueFilter("", false, false, defaults.copy(sort = SortOrder.AUTHOR)).sort)
    }

    @Test fun unknownSortValueFallsBackToTitle() {
        assertEquals(SortOrder.TITLE, catalogueFilter("", false, false, defaults.copy(sort = "DISTANCE")).sort)
        assertEquals(SortOrder.TITLE, SortOrder.normalize(null))
    }

    @Test fun searchTextIsTrimmedAndCountsAsAFilter() {
        val filter = catalogueFilter("  dune ", false, false, defaults)
        assertEquals("dune", filter.query)
        assertTrue(filter.isNarrowed)
        assertFalse(catalogueFilter("   ", false, false, defaults).isNarrowed)
    }
}

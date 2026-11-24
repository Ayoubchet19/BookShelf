package fr.estiam.traillog.bookshelf.domain

import fr.estiam.traillog.bookshelf.model.SortOrder
import fr.estiam.traillog.bookshelf.model.UserPreferences

/** Everything the catalogue query depends on. A new value rebuilds the Pager. */
data class CatalogueFilter(
    val query: String = "",
    val favoritesOnly: Boolean = false,
    val completedOnly: Boolean = false,
    val hideCompleted: Boolean = false,
    val sort: String = SortOrder.TITLE
) {
    /** True when the user narrowed the list (used to pick the empty-state message). */
    val isNarrowed: Boolean get() = query.isNotEmpty() || favoritesOnly || completedOnly || hideCompleted
}

/**
 * Combines the screen filters with the stored preferences.
 * When "Show completed books" is off, completed books are hidden and the
 * "Completed" filter cannot apply (it would always give an empty list).
 */
fun catalogueFilter(query: String, favoritesOnly: Boolean, completedOnly: Boolean, preferences: UserPreferences) =
    CatalogueFilter(
        query = query.trim(),
        favoritesOnly = favoritesOnly,
        completedOnly = completedOnly && preferences.showCompleted,
        hideCompleted = !preferences.showCompleted,
        sort = SortOrder.normalize(preferences.sort)
    )

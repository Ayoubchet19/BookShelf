package fr.estiam.traillog.bookshelf.domain

import fr.estiam.traillog.bookshelf.model.Category

/**
 * Pure rules of the categories CRUD (CategoriesActivity).
 * Covered by JUnit (CategoryRulesTest).
 */

const val CATEGORY_NAME_MIN_LENGTH = 2
const val CATEGORY_NAME_MAX_LENGTH = 30

enum class CategoryError {
    NAME_REQUIRED, NAME_TOO_SHORT, NAME_TOO_LONG,
    /** Another category already has this name (case-insensitive). */
    DUPLICATE_NAME,
    NOT_FOUND,
    /** The category is still used by books: deleting it would break their foreign key. */
    IN_USE
}

/** Thrown by the repository when a category write is rejected; nothing is written. */
class CategoryValidationException(val error: CategoryError, val bookCount: Int = 0) :
    IllegalArgumentException("Category rejected: $error")

/** Display form of a name: trimmed, inner whitespace collapsed. */
fun normalizeCategoryName(name: String): String = cleanText(name)

/**
 * Stable id derived from a name ("Science Fiction" -> "science_fiction").
 * Same scheme as the JSON import, so imported and user-created categories match.
 */
fun categoryIdFor(name: String): String = normalizeCategoryName(name).lowercase().replace(" ", "_")

/**
 * Validates a new name.
 * @param existing every category currently stored.
 * @param editingId id of the category being renamed (its own name is not a duplicate), null on creation.
 * @return the error, or null when the name is accepted.
 */
fun validateCategoryName(name: String, existing: List<Category>, editingId: String? = null): CategoryError? {
    val clean = normalizeCategoryName(name)
    return when {
        clean.isEmpty() -> CategoryError.NAME_REQUIRED
        clean.length < CATEGORY_NAME_MIN_LENGTH -> CategoryError.NAME_TOO_SHORT
        clean.length > CATEGORY_NAME_MAX_LENGTH -> CategoryError.NAME_TOO_LONG
        existing.any { it.id != editingId && it.name.equals(clean, ignoreCase = true) } -> CategoryError.DUPLICATE_NAME
        else -> null
    }
}

/**
 * Id for a new category. The id never changes on rename (books point to it), so a
 * renamed category can still own the id of its old name: in that case a suffix is added.
 */
fun newCategoryId(name: String, takenIds: Set<String>): String {
    val base = categoryIdFor(name)
    if (base !in takenIds) return base
    var n = 2
    while ("${base}_$n" in takenIds) n++
    return "${base}_$n"
}

/** A category can only be deleted when no book uses it. */
fun checkCategoryDeletion(bookCount: Int): CategoryError? = if (bookCount > 0) CategoryError.IN_USE else null

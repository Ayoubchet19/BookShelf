package fr.estiam.traillog.bookshelf.domain

/**
 * Pure validation of the add/edit book form (EditBookActivity).
 * No Android dependency, so every rule is covered by JUnit (BookFormTest).
 */

const val TITLE_MAX_LENGTH = 120
const val AUTHOR_MAX_LENGTH = 80
const val NOTES_MAX_LENGTH = 500
const val PAGE_COUNT_MAX = 20_000

/** Raw text typed by the user. [categoryId] is the category picked in the dropdown (null = none). */
data class BookFormInput(
    val title: String,
    val author: String,
    val pageCount: String,
    val categoryId: String?,
    val notes: String = ""
)

enum class BookFormError {
    TITLE_REQUIRED, TITLE_TOO_LONG,
    AUTHOR_REQUIRED, AUTHOR_TOO_LONG,
    PAGES_REQUIRED, PAGES_NOT_A_NUMBER, PAGES_OUT_OF_RANGE,
    /** The new page count is lower than the pages already recorded in reading sessions. */
    PAGES_BELOW_PAGES_READ,
    CATEGORY_REQUIRED, CATEGORY_NOT_FOUND,
    NOTES_TOO_LONG
}

/** One error (or null) per form field, so each one is shown under its own field. */
data class BookFormErrors(
    val title: BookFormError? = null,
    val author: BookFormError? = null,
    val pageCount: BookFormError? = null,
    val category: BookFormError? = null,
    val notes: BookFormError? = null
) {
    val isValid: Boolean get() = title == null && author == null && pageCount == null && category == null && notes == null
}

/** Cleaned values, only produced when the whole form is valid. */
data class ValidBookForm(
    val title: String,
    val author: String,
    val pageCount: Int,
    val categoryId: String,
    val notes: String
)

/** Result of [validateBookForm]: either the cleaned values or the per-field errors. */
sealed interface BookFormResult {
    data class Valid(val value: ValidBookForm) : BookFormResult
    data class Invalid(val errors: BookFormErrors) : BookFormResult
}

/** Thrown by the repository when a form is rejected; nothing is written. */
class BookFormValidationException(val errors: BookFormErrors) :
    IllegalArgumentException("Invalid book form: $errors")

/** Trims and collapses inner whitespace ("  The   Hobbit " -> "The Hobbit"). */
fun cleanText(value: String): String = value.trim().replace(Regex("\\s+"), " ")

/**
 * Validates the form.
 * @param pagesAlreadyRead pages already recorded for the book being edited (0 for a new book):
 *   the page count cannot go below it, otherwise progress would exceed 100 %.
 * @param existingCategoryIds when not null, the picked category must be one of them.
 */
fun validateBookForm(
    input: BookFormInput,
    pagesAlreadyRead: Int = 0,
    existingCategoryIds: Set<String>? = null
): BookFormResult {
    val title = cleanText(input.title)
    val author = cleanText(input.author)
    val notes = input.notes.trim()
    val pagesText = input.pageCount.trim()
    val pages = pagesText.toIntOrNull()

    val errors = BookFormErrors(
        title = when {
            title.isEmpty() -> BookFormError.TITLE_REQUIRED
            title.length > TITLE_MAX_LENGTH -> BookFormError.TITLE_TOO_LONG
            else -> null
        },
        author = when {
            author.isEmpty() -> BookFormError.AUTHOR_REQUIRED
            author.length > AUTHOR_MAX_LENGTH -> BookFormError.AUTHOR_TOO_LONG
            else -> null
        },
        pageCount = when {
            pagesText.isEmpty() -> BookFormError.PAGES_REQUIRED
            pages == null -> BookFormError.PAGES_NOT_A_NUMBER
            pages !in 1..PAGE_COUNT_MAX -> BookFormError.PAGES_OUT_OF_RANGE
            pages < pagesAlreadyRead -> BookFormError.PAGES_BELOW_PAGES_READ
            else -> null
        },
        category = when {
            input.categoryId.isNullOrBlank() -> BookFormError.CATEGORY_REQUIRED
            existingCategoryIds != null && input.categoryId !in existingCategoryIds -> BookFormError.CATEGORY_NOT_FOUND
            else -> null
        },
        notes = if (notes.length > NOTES_MAX_LENGTH) BookFormError.NOTES_TOO_LONG else null
    )
    return if (errors.isValid) {
        BookFormResult.Valid(ValidBookForm(title, author, pages!!, input.categoryId!!, notes))
    } else {
        BookFormResult.Invalid(errors)
    }
}

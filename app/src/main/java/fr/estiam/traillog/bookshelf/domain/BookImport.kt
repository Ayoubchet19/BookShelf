package fr.estiam.traillog.bookshelf.domain

/*
 * Pure rules for the initial import of assets/books.json.
 * The repository parses the file into RawBookRecord values, then planImport() decides
 * what is inserted. No Android dependency, so everything here is covered by JUnit.
 */

/** One entry of books.json as read from the file. Any field can be missing or wrong. */
data class RawBookRecord(
    val id: String?,
    val title: String?,
    val author: String?,
    val category: String?,
    val pageCount: Int?,
    val notes: String? = null
)

/** A record that passed validation, ready to be inserted. Strings are trimmed. */
data class ValidBookRecord(
    val id: String,
    val title: String,
    val author: String,
    val categoryId: String,
    val categoryName: String,
    val pageCount: Int,
    val notes: String
)

enum class ImportRejection {
    MISSING_ID,
    DUPLICATE_ID,
    ALREADY_IN_DATABASE,
    BLANK_TITLE,
    BLANK_AUTHOR,
    BLANK_CATEGORY,
    INVALID_PAGE_COUNT
}

/** [index] is the position of the record in the JSON array (0-based), used in the logs. */
data class RejectedRecord(val index: Int, val id: String?, val reason: ImportRejection)

data class ImportPlan(val accepted: List<ValidBookRecord>, val rejected: List<RejectedRecord>) {
    /** Categories to create: id -> display name. */
    val categories: Map<String, String> get() = accepted.associate { it.categoryId to it.categoryName }
}

/** A book cannot have more pages than this (protects against typos such as 3520 -> 35200). */
const val MAX_PAGE_COUNT = 20_000

/** Same id rule as BookRepository.addCategory: "Science Fiction" -> "science_fiction". */
fun categoryIdOf(name: String): String = name.trim().lowercase().replace(" ", "_")

/** Field rules for one record. Id rules (missing, duplicate, already stored) are checked by [planImport]. */
fun validateRecord(record: RawBookRecord): ImportRejection? = when {
    record.title.isNullOrBlank() -> ImportRejection.BLANK_TITLE
    record.author.isNullOrBlank() -> ImportRejection.BLANK_AUTHOR
    record.category.isNullOrBlank() -> ImportRejection.BLANK_CATEGORY
    record.pageCount == null || record.pageCount !in 1..MAX_PAGE_COUNT -> ImportRejection.INVALID_PAGE_COUNT
    else -> null
}

/**
 * Splits the file's records into the ones to insert and the ones to skip.
 * - A record whose id is already in the database is skipped, so user data is never overwritten.
 * - When the file holds the same id twice, the first valid record wins.
 */
fun planImport(records: List<RawBookRecord>, existingIds: Set<String> = emptySet()): ImportPlan {
    val seen = HashSet<String>()
    val accepted = mutableListOf<ValidBookRecord>()
    val rejected = mutableListOf<RejectedRecord>()
    records.forEachIndexed { index, record ->
        val id = record.id?.trim().orEmpty()
        val reason = when {
            id.isEmpty() -> ImportRejection.MISSING_ID
            id in existingIds -> ImportRejection.ALREADY_IN_DATABASE
            id in seen -> ImportRejection.DUPLICATE_ID
            else -> validateRecord(record)
        }
        if (reason != null) {
            rejected += RejectedRecord(index, record.id, reason)
        } else {
            seen += id
            val category = record.category.orEmpty().trim()
            accepted += ValidBookRecord(
                id = id,
                title = record.title.orEmpty().trim(),
                author = record.author.orEmpty().trim(),
                categoryId = categoryIdOf(category),
                categoryName = category,
                pageCount = record.pageCount ?: 0,
                notes = record.notes?.trim().orEmpty()
            )
        }
    }
    return ImportPlan(accepted, rejected)
}

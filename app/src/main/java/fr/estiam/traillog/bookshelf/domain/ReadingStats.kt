package fr.estiam.traillog.bookshelf.domain

import fr.estiam.traillog.bookshelf.model.Book
import fr.estiam.traillog.bookshelf.model.ReadingSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil

/*
 * Pure reading-session rules and statistics.
 *
 * Nothing in this file depends on Android, so every rule can be covered by plain
 * JUnit tests (see app/src/test/.../ReadingStatsTest.kt). The repository and the
 * ViewModels only call these functions; they never duplicate the arithmetic.
 */

/** Progress of a book derived from its sessions. */
data class BookProgress(val pagesRead: Int, val isCompleted: Boolean)

/**
 * Recomputes a book's progress from all of its sessions.
 * The sum of session pages is capped to [pageCount]; the book is completed when the cap is reached.
 */
fun computeProgress(pageCount: Int, sessions: List<ReadingSession>): BookProgress {
    if (pageCount <= 0) return BookProgress(0, false)
    val read = sessions.sumOf { it.pagesRead }.coerceIn(0, pageCount)
    return BookProgress(read, read == pageCount)
}

/** Reasons a reading session can be rejected. */
enum class SessionError { BOOK_NOT_FOUND, SESSION_NOT_FOUND, PAGES_NOT_POSITIVE, MINUTES_NOT_POSITIVE, PAGES_EXCEED_REMAINING }

/**
 * Thrown by the repository when a session write breaks a business rule.
 * It extends [IllegalArgumentException] so existing `catch (IllegalArgumentException)` blocks keep working.
 * [remainingPages] is the maximum accepted page count, useful for the error message.
 */
class SessionValidationException(val error: SessionError, val remainingPages: Int = 0) :
    IllegalArgumentException("Invalid reading session: $error (remaining pages: $remainingPages)")

/**
 * Validates a session of [pages]/[minutes] for a book of [pageCount] pages where
 * [pagesAlreadyRead] pages are already covered by the *other* sessions of the book.
 * Returns null when the session is valid.
 */
fun validateSession(pages: Int, minutes: Int, pageCount: Int, pagesAlreadyRead: Int): SessionError? {
    val remaining = remainingPages(pageCount, pagesAlreadyRead)
    return when {
        pages <= 0 -> SessionError.PAGES_NOT_POSITIVE
        minutes <= 0 -> SessionError.MINUTES_NOT_POSITIVE
        pages > remaining -> SessionError.PAGES_EXCEED_REMAINING
        else -> null
    }
}

/** Pages left to read, never negative. */
fun remainingPages(pageCount: Int, pagesRead: Int): Int = (pageCount - pagesRead).coerceAtLeast(0)

/** Average reading speed in pages per hour, or null when no time has been recorded. */
fun pagesPerHour(sessions: List<ReadingSession>): Double? {
    val minutes = sessions.sumOf { it.durationMinutes.toLong() }
    if (minutes <= 0) return null
    return sessions.sumOf { it.pagesRead.toLong() } * 60.0 / minutes
}

/**
 * Estimated minutes needed to read [remainingPages] at [pagesPerHour] (rounded up).
 * Returns 0 for a finished book and null when the speed is unknown.
 */
fun estimatedMinutesRemaining(remainingPages: Int, pagesPerHour: Double?): Int? {
    if (remainingPages <= 0) return 0
    if (pagesPerHour == null || pagesPerHour <= 0.0) return null
    return ceil(remainingPages * 60.0 / pagesPerHour).toInt()
}

/**
 * Number of consecutive reading days ending today, or ending yesterday when nothing
 * has been read yet today (the streak is not broken before the day is over).
 * Several sessions on the same day count once. Days are computed in [zone].
 */
fun readingStreak(startedAt: List<Long>, today: LocalDate, zone: ZoneId): Int {
    val days = startedAt.map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }.toSet()
    var day = when {
        today in days -> today
        today.minusDays(1) in days -> today.minusDays(1)
        else -> return 0
    }
    var streak = 0
    while (day in days) { streak++; day = day.minusDays(1) }
    return streak
}

/** Aggregated figures for one category. */
data class CategoryStat(val categoryId: String, val categoryName: String, val bookCount: Int, val pagesRead: Int, val minutes: Int)

/** Groups books and sessions by category, sorted by category name. Categories without books are omitted. */
fun categoryStats(books: List<Book>, sessions: List<ReadingSession>): List<CategoryStat> {
    val minutesByBook = sessions.groupBy { it.bookId }.mapValues { (_, list) -> list.sumOf { it.durationMinutes } }
    return books.groupBy { it.categoryId }.map { (categoryId, categoryBooks) ->
        CategoryStat(
            categoryId = categoryId,
            categoryName = categoryBooks.first().categoryName.ifBlank { categoryId },
            bookCount = categoryBooks.size,
            pagesRead = categoryBooks.sumOf { it.pagesRead },
            minutes = categoryBooks.sumOf { minutesByBook[it.id] ?: 0 }
        )
    }.sortedBy { it.categoryName.lowercase() }
}

/** Everything the statistics screen displays. */
data class StatisticsSummary(
    val completedBooks: Int,
    val favoriteBooks: Int,
    val pagesRead: Int,
    val totalMinutes: Int,
    val averagePagesPerHour: Double?,
    val streakDays: Int,
    val categories: List<CategoryStat>,
    val sessionCount: Int
) {
    /** True when there is nothing meaningful to show yet. */
    val isEmpty: Boolean get() = sessionCount == 0 && completedBooks == 0 && favoriteBooks == 0
}

fun buildStatistics(books: List<Book>, sessions: List<ReadingSession>, today: LocalDate, zone: ZoneId) = StatisticsSummary(
    completedBooks = books.count { it.isCompleted },
    favoriteBooks = books.count { it.isFavorite },
    pagesRead = books.sumOf { it.pagesRead },
    totalMinutes = sessions.sumOf { it.durationMinutes },
    averagePagesPerHour = pagesPerHour(sessions),
    streakDays = readingStreak(sessions.map { it.startedAt }, today, zone),
    categories = categoryStats(books, sessions),
    sessionCount = sessions.size
)

package fr.estiam.traillog.bookshelf

import fr.estiam.traillog.bookshelf.domain.*
import fr.estiam.traillog.bookshelf.model.Book
import fr.estiam.traillog.bookshelf.model.ReadingSession
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Unit tests of the pure functions in domain/ReadingStats.kt (no Android needed). */
class ReadingStatsTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val today: LocalDate = LocalDate.of(2026, 11, 23)

    private fun session(pages: Int, minutes: Int = 30, bookId: String = "b1", at: Long = 0L, id: String = "s$pages-$minutes-$at") =
        ReadingSession(id, bookId, pages, minutes, at)

    /** Epoch millis for [day] at [hour]:00 in [zone]. */
    private fun at(day: LocalDate, hour: Int = 12) = day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    // --- progress ---------------------------------------------------------------------------

    @Test fun progressIsTheSumOfSessionPages() {
        assertEquals(BookProgress(70, false), computeProgress(100, listOf(session(30), session(40))))
    }

    @Test fun bookIsCompletedAfterTheLastSession() {
        val sessions = listOf(session(60), session(40))
        assertEquals(BookProgress(100, true), computeProgress(100, sessions))
    }

    @Test fun completedBookGoesBackToInProgressWhenASessionIsDeleted() {
        val sessions = listOf(session(60, id = "a"), session(40, id = "b"))
        assertTrue(computeProgress(100, sessions).isCompleted)
        val afterDelete = computeProgress(100, sessions.filterNot { it.id == "b" })
        assertEquals(BookProgress(60, false), afterDelete)
    }

    @Test fun progressIsCappedToPageCount() {
        assertEquals(BookProgress(100, true), computeProgress(100, listOf(session(80), session(80))))
    }

    @Test fun progressWithoutSessionsIsZero() {
        assertEquals(BookProgress(0, false), computeProgress(100, emptyList()))
    }

    // --- validation -------------------------------------------------------------------------

    @Test fun validSessionIsAccepted() {
        assertNull(validateSession(pages = 20, minutes = 15, pageCount = 100, pagesAlreadyRead = 50))
    }

    @Test fun zeroPagesAreRejected() {
        assertEquals(SessionError.PAGES_NOT_POSITIVE, validateSession(0, 15, 100, 0))
    }

    @Test fun negativePagesAreRejected() {
        assertEquals(SessionError.PAGES_NOT_POSITIVE, validateSession(-5, 15, 100, 0))
    }

    @Test fun zeroMinutesAreRejected() {
        assertEquals(SessionError.MINUTES_NOT_POSITIVE, validateSession(10, 0, 100, 0))
    }

    @Test fun exactlyTheRemainingPagesAreAccepted() {
        assertNull(validateSession(pages = 30, minutes = 10, pageCount = 100, pagesAlreadyRead = 70))
    }

    @Test fun moreThanTheRemainingPagesAreRejected() {
        assertEquals(SessionError.PAGES_EXCEED_REMAINING, validateSession(31, 10, 100, 70))
    }

    @Test fun anySessionOnACompletedBookIsRejected() {
        assertEquals(SessionError.PAGES_EXCEED_REMAINING, validateSession(1, 10, 100, 100))
    }

    @Test fun remainingPagesAreNeverNegative() {
        assertEquals(0, remainingPages(100, 120))
        assertEquals(25, remainingPages(100, 75))
    }

    // --- speed and remaining time -----------------------------------------------------------

    @Test fun speedIsPagesPerHour() {
        // 30 pages in 30 min + 30 pages in 60 min = 60 pages in 1.5 h = 40 pages/h
        assertEquals(40.0, pagesPerHour(listOf(session(30, 30), session(30, 60)))!!, 0.0001)
    }

    @Test fun speedIsUnknownWithoutSessions() {
        assertNull(pagesPerHour(emptyList()))
    }

    @Test fun remainingTimeIsRoundedUp() {
        // 50 pages at 40 pages/h = 75 min
        assertEquals(75, estimatedMinutesRemaining(50, 40.0))
        // 1 page at 40 pages/h = 1.5 min -> 2 min
        assertEquals(2, estimatedMinutesRemaining(1, 40.0))
    }

    @Test fun remainingTimeIsZeroForAFinishedBookAndUnknownWithoutSpeed() {
        assertEquals(0, estimatedMinutesRemaining(0, null))
        assertNull(estimatedMinutesRemaining(10, null))
    }

    // --- streak -----------------------------------------------------------------------------

    @Test fun streakCountsConsecutiveDaysEndingToday() {
        val days = listOf(today, today.minusDays(1), today.minusDays(2)).map { at(it) }
        assertEquals(3, readingStreak(days, today, zone))
    }

    @Test fun streakStopsAtAOneDayGap() {
        // today, yesterday, (gap two days ago), three and four days ago
        val days = listOf(today, today.minusDays(1), today.minusDays(3), today.minusDays(4)).map { at(it) }
        assertEquals(2, readingStreak(days, today, zone))
    }

    @Test fun streakIsKeptWhenNothingWasReadYetToday() {
        val days = listOf(today.minusDays(1), today.minusDays(2)).map { at(it) }
        assertEquals(2, readingStreak(days, today, zone))
    }

    @Test fun streakIsZeroWhenLastSessionIsOlderThanYesterday() {
        assertEquals(0, readingStreak(listOf(at(today.minusDays(2))), today, zone))
        assertEquals(0, readingStreak(emptyList(), today, zone))
    }

    @Test fun severalSessionsOnTheSameDayCountOnce() {
        val days = listOf(at(today, 8), at(today, 21), at(today.minusDays(1), 23))
        assertEquals(2, readingStreak(days, today, zone))
    }

    // --- categories and summary -------------------------------------------------------------

    private fun book(id: String, category: String, pagesRead: Int, favorite: Boolean = false, completed: Boolean = false) =
        Book(id, "Title $id", "Author", category.lowercase(), category, pageCount = 100, pagesRead = pagesRead,
            isFavorite = favorite, isCompleted = completed)

    @Test fun categoryStatsGroupBooksPagesAndMinutes() {
        val books = listOf(book("1", "Tech", 100, completed = true), book("2", "Tech", 20), book("3", "Novel", 10))
        val sessions = listOf(session(100, 120, "1"), session(20, 30, "2"), session(10, 15, "3"))
        val stats = categoryStats(books, sessions)
        assertEquals(listOf("Novel", "Tech"), stats.map { it.categoryName })
        assertEquals(CategoryStat("tech", "Tech", 2, 120, 150), stats[1])
        assertEquals(CategoryStat("novel", "Novel", 1, 10, 15), stats[0])
    }

    @Test fun summaryAggregatesAllFigures() {
        val books = listOf(book("1", "Tech", 100, favorite = true, completed = true), book("2", "Novel", 20))
        val sessions = listOf(session(100, 120, "1", at(today)), session(20, 30, "2", at(today.minusDays(1))))
        val summary = buildStatistics(books, sessions, today, zone)
        assertEquals(1, summary.completedBooks)
        assertEquals(1, summary.favoriteBooks)
        assertEquals(120, summary.pagesRead)
        assertEquals(150, summary.totalMinutes)
        assertEquals(48.0, summary.averagePagesPerHour!!, 0.0001)
        assertEquals(2, summary.streakDays)
        assertFalse(summary.isEmpty)
    }

    @Test fun summaryIsEmptyWithoutSessionsFavoritesOrCompletedBooks() {
        assertTrue(buildStatistics(listOf(book("1", "Tech", 0)), emptyList(), today, zone).isEmpty)
    }
}

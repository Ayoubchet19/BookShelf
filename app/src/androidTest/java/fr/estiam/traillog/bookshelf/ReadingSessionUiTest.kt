package fr.estiam.traillog.bookshelf

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.estiam.traillog.R
import fr.estiam.traillog.bookshelf.model.Book
import fr.estiam.traillog.bookshelf.ui.BookDetailActivity
import fr.estiam.traillog.bookshelf.ui.StatisticsActivity
import fr.estiam.traillog.bookshelf.domain.readingStreak
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Espresso tests of the reading-session screens. Each test creates its own book in the
 * app database and deletes it afterwards (its sessions are removed by cascade).
 *
 * Room writes run on a background executor that Espresso does not track, so assertions
 * that follow a write use [eventually] (retry for up to 5 s) instead of a fixed sleep.
 */
@RunWith(AndroidJUnit4::class)
class ReadingSessionUiTest {
    private val app = ApplicationProvider.getApplicationContext<BookshelfApplication>()
    private val bookId = "espresso-" + UUID.randomUUID()

    @Before fun createBook() = runBlocking {
        app.repository.addCategory("Espresso Tests")
        app.repository.save(Book(bookId, "Espresso Book", "Test Author", "espresso_tests", pageCount = 100))
    }

    @After fun deleteBook() = runBlocking { app.repository.delete(bookId) }

    private fun launchDetail(): ActivityScenario<BookDetailActivity> = ActivityScenario.launch(
        Intent(app, BookDetailActivity::class.java).putExtra(BookDetailActivity.EXTRA_BOOK_ID, bookId))

    private fun eventually(assertion: () -> Unit) {
        val deadline = System.currentTimeMillis() + 5_000
        while (true) {
            try { assertion(); return } catch (e: Throwable) {
                if (System.currentTimeMillis() > deadline) throw e
                Thread.sleep(100)
            }
        }
    }

    private fun enterSession(pages: String, minutes: String) {
        onView(withId(R.id.session_pages_input)).perform(scrollTo(), replaceText(pages))
        onView(withId(R.id.session_minutes_input)).perform(scrollTo(), replaceText(minutes), closeSoftKeyboard())
        onView(withId(R.id.save_session_button)).perform(scrollTo(), click())
    }

    @Test fun validSessionAppearsInListAndUpdatesProgress() {
        launchDetail().use {
            onView(withId(R.id.sessions_empty)).perform(scrollTo()).check(matches(withText(R.string.sessions_empty)))
            enterSession("25", "30")
            eventually {
                onView(allOf(withId(R.id.session_details), withText(app.getString(R.string.session_details, 25, 30))))
                    .perform(scrollTo()).check(matches(isDisplayed()))
            }
            onView(withId(R.id.sessions_empty)).check(matches(withEffectiveVisibility(Visibility.GONE)))
            onView(withId(R.id.book_progress)).check(matches(withText(app.getString(R.string.book_progress, 25, 25, 100))))
            // 75 pages left at 50 pages/h -> 1 h 30 min
            onView(withId(R.id.remaining_time)).check(matches(withText(
                app.getString(R.string.remaining_time, app.getString(R.string.duration_hours_minutes, 1, 30), 75))))
        }
    }

    @Test fun zeroPagesShowsAnErrorAndRecordsNothing() {
        launchDetail().use {
            enterSession("0", "30")
            eventually { onView(withText(R.string.error_pages_positive)).check(matches(isDisplayed())) }
            onView(withId(R.id.sessions_empty)).perform(scrollTo()).check(matches(isDisplayed()))
        }
    }

    @Test fun tooManyPagesShowsTheRemainingPages() {
        launchDetail().use {
            enterSession("150", "30")
            eventually { onView(withText(app.getString(R.string.error_pages_exceed, 100))).check(matches(isDisplayed())) }
        }
    }

    @Test fun emptyMinutesShowsANumberError() {
        launchDetail().use {
            enterSession("10", "")
            onView(withText(R.string.error_number_required)).check(matches(isDisplayed()))
        }
    }

    @Test fun deletingASessionAsksForConfirmationAndReopensTheBook() {
        runBlocking { app.repository.addSession(bookId, 100, 60) }
        launchDetail().use {
            eventually { onView(withId(R.id.book_status)).check(matches(withText(R.string.book_completed_label))) }
            onView(withId(R.id.delete_session_button)).perform(scrollTo(), click())
            onView(withText(R.string.session_delete_title)).check(matches(isDisplayed()))
            onView(allOf(withId(android.R.id.button1), withText(R.string.session_delete))).perform(click())
            eventually { onView(withId(R.id.sessions_empty)).perform(scrollTo()).check(matches(isDisplayed())) }
            onView(withId(R.id.book_status)).perform(scrollTo()).check(matches(withText(R.string.book_in_progress_label)))
        }
    }

    @Test fun editModeAndErrorsSurviveRecreation() {
        runBlocking { app.repository.addSession(bookId, 20, 10) }
        launchDetail().use { scenario ->
            eventually { onView(withId(R.id.edit_session_button)).perform(scrollTo(), click()) }
            onView(withId(R.id.session_pages_input)).perform(scrollTo(), replaceText("0"), closeSoftKeyboard())
            onView(withId(R.id.save_session_button)).perform(scrollTo(), click())
            eventually { onView(withText(R.string.error_pages_positive)).check(matches(isDisplayed())) }

            scenario.recreate() // same path as a rotation: activity destroyed, ViewModel kept

            onView(withId(R.id.save_session_button)).perform(scrollTo()).check(matches(withText(R.string.session_update)))
            onView(withId(R.id.session_pages_input)).check(matches(withText("0")))
            onView(withText(R.string.error_pages_positive)).check(matches(isDisplayed()))
        }
    }

    @Test fun pendingDeleteDialogSurvivesRecreation() {
        runBlocking { app.repository.addSession(bookId, 20, 10) }
        launchDetail().use { scenario ->
            eventually { onView(withId(R.id.delete_session_button)).perform(scrollTo(), click()) }
            onView(withText(R.string.session_delete_title)).check(matches(isDisplayed()))
            scenario.recreate()
            onView(withText(R.string.session_delete_title)).check(matches(isDisplayed()))
            onView(allOf(withId(android.R.id.button2), withText(R.string.session_cancel_edit))).perform(click())
            onView(withId(R.id.delete_session_button)).perform(scrollTo()).check(matches(isDisplayed()))
        }
    }

    @Test fun statisticsShowSessionDataAndSurviveRecreation() {
        runBlocking { app.repository.addSession(bookId, 30, 45) }
        ActivityScenario.launch(StatisticsActivity::class.java).use { scenario ->
            eventually { onView(withId(R.id.stats_content)).check(matches(isDisplayed())) }
            onView(withId(R.id.stats_streak)).check(matches(withText(app.getString(R.string.stats_streak) + " : " + streakText())))
            onView(allOf(withId(R.id.category_name), withText("Espresso Tests"))).perform(scrollTo()).check(matches(isDisplayed()))
            scenario.recreate()
            onView(withId(R.id.stats_content)).check(matches(isDisplayed()))
            onView(allOf(withId(R.id.category_name), withText("Espresso Tests"))).perform(scrollTo()).check(matches(isDisplayed()))
        }
    }

    /** Expected streak text: it depends on the other sessions already in the device database. */
    private fun streakText(): String {
        val streak = runBlocking {
            val sessions = app.repository.allSessions.first()
            readingStreak(sessions.map { it.startedAt }, LocalDate.now(), ZoneId.systemDefault())
        }
        return app.getString(R.string.stats_streak_value, streak)
    }
}

package fr.estiam.traillog.bookshelf

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.textfield.TextInputLayout
import fr.estiam.traillog.R
import fr.estiam.traillog.bookshelf.ui.CategoriesActivity
import fr.estiam.traillog.bookshelf.ui.EditBookActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.TypeSafeMatcher
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Espresso tests of the book form and the categories screen. They use the real app
 * database: each test works with uniquely named data and removes it afterwards.
 * Room writes run off the main thread, so checks after a write use [eventually].
 */
@RunWith(AndroidJUnit4::class)
class FormsCategoriesUiTest {
    private val app = ApplicationProvider.getApplicationContext<BookshelfApplication>()
    private val suffix = UUID.randomUUID().toString().take(6)
    private val categoryName = "Espresso Cat $suffix"
    private val bookTitle = "Espresso Form $suffix"

    @After fun cleanUp() = runBlocking {
        app.repository.allBooks.first().filter { it.title == bookTitle }.forEach { app.repository.delete(it.id) }
        app.repository.categorySummaries.first().filter { it.name.startsWith("Espresso Cat $suffix") }
            .forEach { runCatching { app.repository.deleteCategory(it.id) } }
    }

    private fun eventually(assertion: () -> Unit) {
        val deadline = System.currentTimeMillis() + 5_000
        while (true) {
            try { assertion(); return } catch (e: Throwable) {
                if (System.currentTimeMillis() > deadline) throw e
                Thread.sleep(100)
            }
        }
    }

    private fun hasTextInputError(text: String): Matcher<android.view.View> = object : TypeSafeMatcher<android.view.View>() {
        override fun describeTo(description: Description) { description.appendText("TextInputLayout with error \"$text\"") }
        override fun matchesSafely(view: android.view.View) = view is TextInputLayout && view.error?.toString() == text
    }

    @Test fun emptyFormShowsAnErrorUnderEachRequiredField() {
        ActivityScenario.launch(EditBookActivity::class.java).use {
            onView(withId(R.id.save_button)).perform(scrollTo(), click())
            onView(withId(R.id.title_layout)).check(matches(hasTextInputError(app.getString(R.string.form_error_title_required))))
            onView(withId(R.id.author_layout)).check(matches(hasTextInputError(app.getString(R.string.form_error_author_required))))
            onView(withId(R.id.pages_layout)).check(matches(hasTextInputError(app.getString(R.string.form_error_pages_required))))
        }
    }

    @Test fun formErrorsSurviveRecreation() {
        ActivityScenario.launch(EditBookActivity::class.java).use { scenario ->
            onView(withId(R.id.title_input)).perform(scrollTo(), replaceText("Kept title"))
            onView(withId(R.id.pages_input)).perform(scrollTo(), replaceText("0"), closeSoftKeyboard())
            onView(withId(R.id.save_button)).perform(scrollTo(), click())
            onView(withId(R.id.pages_layout)).check(matches(hasTextInputError(app.getString(R.string.form_error_pages_range, 20_000))))

            scenario.recreate() // same path as a rotation

            onView(withId(R.id.title_input)).check(matches(withText("Kept title")))
            onView(withId(R.id.pages_layout)).check(matches(hasTextInputError(app.getString(R.string.form_error_pages_range, 20_000))))
        }
    }

    @Test fun validFormCreatesTheBook() {
        val categoryId = runBlocking { app.repository.createCategory(categoryName) }
        ActivityScenario.launch(EditBookActivity::class.java).use {
            onView(withId(R.id.title_input)).perform(scrollTo(), replaceText(bookTitle))
            onView(withId(R.id.author_input)).perform(scrollTo(), replaceText("Test Author"))
            onView(withId(R.id.pages_input)).perform(scrollTo(), replaceText("250"), closeSoftKeyboard())
            eventually { onView(withId(R.id.category_spinner)).perform(scrollTo(), click()) }
            onData(allOf(`is`(instanceOf(String::class.java)), `is`(categoryName))).perform(click())
            onView(withId(R.id.save_button)).perform(scrollTo(), click())
        }
        eventually {
            val book = runBlocking { app.repository.allBooks.first() }.single { it.title == bookTitle }
            assertEquals(categoryId, book.categoryId)
            assertEquals(250, book.pageCount)
        }
    }

    @Test fun addRenameAndDeleteACategory() {
        ActivityScenario.launch(CategoriesActivity::class.java).use {
            onView(withId(R.id.category_name_input)).perform(replaceText(categoryName), closeSoftKeyboard())
            onView(withId(R.id.save_category_button)).perform(click())
            eventually { onView(allOf(withId(R.id.category_item_name), withText(categoryName))).check(matches(isDisplayed())) }

            // Rename
            onView(allOf(withId(R.id.edit_category_button), withParent(hasDescendant(withText(categoryName))))).perform(click())
            onView(withId(R.id.save_category_button)).check(matches(withText(R.string.category_rename)))
            onView(withId(R.id.category_name_input)).perform(replaceText("$categoryName v2"), closeSoftKeyboard())
            onView(withId(R.id.save_category_button)).perform(click())
            eventually { onView(allOf(withId(R.id.category_item_name), withText("$categoryName v2"))).check(matches(isDisplayed())) }

            // Delete with confirmation
            onView(allOf(withId(R.id.delete_category_button), withParent(hasDescendant(withText("$categoryName v2"))))).perform(click())
            onView(withText(R.string.category_delete_title)).check(matches(isDisplayed()))
            onView(allOf(withId(android.R.id.button1), withText(R.string.category_delete))).perform(click())
            eventually {
                val names = runBlocking { app.repository.categorySummaries.first() }.map { it.name }
                assertFalse(names.contains("$categoryName v2"))
            }
        }
    }

    @Test fun renameModeAndErrorSurviveRecreation() {
        runBlocking { app.repository.createCategory(categoryName) }
        ActivityScenario.launch(CategoriesActivity::class.java).use { scenario ->
            eventually {
                onView(allOf(withId(R.id.edit_category_button), withParent(hasDescendant(withText(categoryName))))).perform(click())
            }
            onView(withId(R.id.category_name_input)).perform(replaceText(""), closeSoftKeyboard())
            onView(withId(R.id.save_category_button)).perform(click())
            onView(withId(R.id.category_name_layout)).check(matches(hasTextInputError(app.getString(R.string.category_error_required))))

            scenario.recreate()

            onView(withId(R.id.save_category_button)).check(matches(withText(R.string.category_rename)))
            onView(withId(R.id.category_name_layout)).check(matches(hasTextInputError(app.getString(R.string.category_error_required))))
        }
    }

    @Test fun duplicateCategoryShowsAnError() {
        runBlocking { app.repository.createCategory(categoryName) }
        ActivityScenario.launch(CategoriesActivity::class.java).use {
            eventually { onView(allOf(withId(R.id.category_item_name), withText(categoryName))).check(matches(isDisplayed())) }
            onView(withId(R.id.category_name_input)).perform(replaceText(categoryName.uppercase()), closeSoftKeyboard())
            onView(withId(R.id.save_category_button)).perform(click())
            eventually {
                onView(withId(R.id.category_name_layout)).check(matches(hasTextInputError(app.getString(R.string.category_error_duplicate))))
            }
        }
    }
}

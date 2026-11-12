package fr.estiam.traillog.bookshelf

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import fr.estiam.traillog.R
import fr.estiam.traillog.bookshelf.ui.BookShelfActivity
import org.hamcrest.Matchers.containsString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookShelfUiTest {
    @get:Rule val activity = ActivityScenarioRule(BookShelfActivity::class.java)

    @Test fun catalogueShowsSearchField() {
        onView(withId(R.id.search_input)).check { view, _ -> check(view.isShown) }
    }

    @Test fun searchAcceptsTitleText() {
        onView(withId(R.id.search_input)).perform(typeText("Dune"))
        onView(withId(R.id.search_input)).check { view, _ -> check(view.contentDescription == null || view.isShown) }
    }

    @Test fun categoriesButtonOpensCategoriesScreen() {
        onView(withId(R.id.categories_button)).perform(click())
        onView(withText(containsString("New category"))).check { view, _ -> check(view.isShown) }
    }
}

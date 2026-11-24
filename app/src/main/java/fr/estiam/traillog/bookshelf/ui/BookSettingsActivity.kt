package fr.estiam.traillog.bookshelf.ui

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import fr.estiam.traillog.R
import fr.estiam.traillog.bookshelf.BookshelfApplication
import fr.estiam.traillog.bookshelf.model.SortOrder
import fr.estiam.traillog.bookshelf.model.UserPreferences
import kotlinx.coroutines.launch

/**
 * Three persistent preferences (Preferences DataStore):
 * - dark mode: applied to the whole app by BookshelfApplication;
 * - show completed books: hides completed books from the catalogue;
 * - sort order: title, author or reading progress.
 */
class BookSettingsActivity : AppCompatActivity() {
    private val app get() = application as BookshelfApplication

    /**
     * Last values read from DataStore. A write only happens when the user picks a value that
     * differs from it, so the callbacks fired while the views are being filled write nothing.
     * (Before this guard, the Spinner's first callback saved TITLE over a stored AUTHOR.)
     */
    private var current: UserPreferences? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_book_settings)
        val dark = findViewById<CompoundButton>(R.id.pref_dark_mode)
        val showCompleted = findViewById<CompoundButton>(R.id.pref_show_completed)
        val sort = findViewById<Spinner>(R.id.pref_sort)
        val labels = listOf(R.string.pref_sort_title, R.string.pref_sort_author, R.string.pref_sort_progress).map { getString(it) }
        sort.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)

        dark.setOnCheckedChangeListener { _, value ->
            val loaded = current ?: return@setOnCheckedChangeListener
            if (loaded.darkMode != value) lifecycleScope.launch { app.preferences.setDarkMode(value) }
        }
        showCompleted.setOnCheckedChangeListener { _, value ->
            val loaded = current ?: return@setOnCheckedChangeListener
            if (loaded.showCompleted != value) lifecycleScope.launch { app.preferences.setShowCompleted(value) }
        }
        sort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val loaded = current ?: return
                val value = SortOrder.ALL[position]
                if (loaded.sort != value) lifecycleScope.launch { app.preferences.setSort(value) }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.preferences.flow.collect { value ->
                    current = value
                    dark.isChecked = value.darkMode
                    showCompleted.isChecked = value.showCompleted
                    sort.setSelection(SortOrder.ALL.indexOf(value.sort).coerceAtLeast(0), false)
                }
            }
        }
    }
}

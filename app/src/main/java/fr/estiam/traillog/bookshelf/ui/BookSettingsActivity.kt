package fr.estiam.traillog.bookshelf.ui

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import fr.estiam.traillog.bookshelf.BookshelfApplication
import kotlinx.coroutines.launch

class BookSettingsActivity : AppCompatActivity() {
    private val app get() = application as BookshelfApplication
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        val dark = Switch(this).apply { text = "Dark mode" }; val completed = Switch(this).apply { text = "Include completed books" }
        val sort = Spinner(this); sort.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("TITLE", "AUTHOR"))
        root.addView(dark); root.addView(completed); root.addView(sort); setContentView(root)
        lifecycleScope.launch {
            app.preferences.flow.collect { value ->
                dark.isChecked = value.darkMode; completed.isChecked = value.showCompleted
                sort.setSelection(if (value.sort == "AUTHOR") 1 else 0)
            }
        }
        dark.setOnCheckedChangeListener { _, value -> lifecycleScope.launch { app.preferences.setDarkMode(value) } }
        completed.setOnCheckedChangeListener { _, value -> lifecycleScope.launch { app.preferences.setShowCompleted(value) } }
        sort.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                lifecycleScope.launch { app.preferences.setSort(if (position == 1) "AUTHOR" else "TITLE") }
            }
        }
    }
}

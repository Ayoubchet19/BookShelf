package fr.estiam.traillog.bookshelf.ui

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import fr.estiam.traillog.bookshelf.BookshelfApplication
import kotlinx.coroutines.launch

class CategoriesActivity : AppCompatActivity() {
    private val app get() = application as BookshelfApplication
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        val input = EditText(this).apply { hint = "New category" }; val add = Button(this).apply { text = "Add category" }
        val list = TextView(this); root.addView(input); root.addView(add); root.addView(list); setContentView(root)
        add.setOnClickListener { lifecycleScope.launch { try { app.repository.addCategory(input.text.toString()); input.text.clear() } catch (_: IllegalArgumentException) { input.error = "Required" } } }
        lifecycleScope.launch { app.repository.categories.collect { list.text = it.joinToString("\n") { category -> "• ${category.name}" } } }
    }
}

package fr.estiam.traillog.bookshelf.ui

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import fr.estiam.traillog.bookshelf.BookshelfApplication
import fr.estiam.traillog.bookshelf.model.Book
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

class EditBookActivity : AppCompatActivity() {
    private val app get() = application as BookshelfApplication
    private var original: Book? = null
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val fields = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        fun field(hint: String) = EditText(this).apply { this.hint = hint }
        val title = field("Title"); val author = field("Author"); val pages = field("Page count")
        val notes = field("Notes"); val category = field("Category name")
        listOf(title, author, pages, category, notes).forEach { fields.addView(it) }
        val save = Button(this).apply { text = "Save" }; fields.addView(save); setContentView(fields)
        val id = intent.getStringExtra("book_id")
        lifecycleScope.launch {
            if (id != null) app.repository.observeBook(id).first()?.let {
                original = it; title.setText(it.title); author.setText(it.author); pages.setText(it.pageCount.toString())
                category.setText(it.categoryName); notes.setText(it.notes)
            }
        }
        save.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val categoryValue = category.text.toString().trim()
                    app.repository.addCategory(categoryValue)
                    val categoryId = categoryValue.lowercase().replace(" ", "_")
                    val value = (original ?: Book(UUID.randomUUID().toString(), "", "", categoryId, pageCount = 1)).copy(
                        title = title.text.toString().trim(), author = author.text.toString().trim(),
                        categoryId = categoryId, categoryName = categoryValue,
                        pageCount = pages.text.toString().toInt(), notes = notes.text.toString())
                    app.repository.save(value); finish()
                } catch (_: Exception) { Toast.makeText(this@EditBookActivity, "Complete all fields with valid values", Toast.LENGTH_LONG).show() }
            }
        }
    }
}

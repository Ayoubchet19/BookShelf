package fr.estiam.traillog.bookshelf.ui

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import fr.estiam.traillog.bookshelf.BookshelfApplication
import kotlinx.coroutines.launch

class BookDetailActivity : AppCompatActivity() {
    private val app get() = application as BookshelfApplication
    private var book: fr.estiam.traillog.bookshelf.model.Book? = null
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        val content = TextView(this).apply { textSize = 18f }
        val favorite = Button(this).apply { text = "Toggle favorite" }
        val edit = Button(this).apply { text = "Edit book" }
        val session = Button(this).apply { text = "Record reading session" }
        val delete = Button(this).apply { text = "Delete book" }
        root.addView(content); root.addView(favorite); root.addView(edit); root.addView(session); root.addView(delete); setContentView(root)
        val id = intent.getStringExtra("book_id") ?: return finish()
        lifecycleScope.launch {
            app.repository.observeBook(id).collect { value ->
                book = value
                content.text = value?.let { "${it.title}\n${it.author}\n${it.categoryName}\nProgress: ${it.progress}% (${it.pagesRead}/${it.pageCount} pages)\n\n${it.notes}" } ?: "Book not found"
            }
        }
        favorite.setOnClickListener { book?.let { value -> lifecycleScope.launch { app.repository.save(value.copy(isFavorite = !value.isFavorite)) } } }
        edit.setOnClickListener { startActivity(android.content.Intent(this, EditBookActivity::class.java).putExtra("book_id", id)) }
        session.setOnClickListener { recordSession(id) }
        delete.setOnClickListener { lifecycleScope.launch { app.repository.delete(id); finish() } }
    }
    private fun recordSession(id: String) {
        val pages = EditText(this).apply { hint = "Pages read"; inputType = 2 }
        val minutes = EditText(this).apply { hint = "Minutes"; inputType = 2 }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(pages); addView(minutes) }
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Reading session").setView(box)
            .setPositiveButton("Save") { _, _ ->
                lifecycleScope.launch {
                    try { app.repository.addSession(id, pages.text.toString().toInt(), minutes.text.toString().toInt()) }
                    catch (_: IllegalArgumentException) { Toast.makeText(this@BookDetailActivity, "Enter positive values", Toast.LENGTH_LONG).show() }
                }
            }.setNegativeButton("Cancel", null).show()
    }
}

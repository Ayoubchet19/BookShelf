package fr.estiam.traillog.bookshelf.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import fr.estiam.traillog.bookshelf.BookshelfApplication
import kotlinx.coroutines.launch

class StatisticsActivity : AppCompatActivity() {
    private val app get() = application as BookshelfApplication
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val text = TextView(this).apply { textSize = 20f; setPadding(32, 32, 32, 32) }; setContentView(text)
        lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(app.repository.completedCount, app.repository.favoriteCount, app.repository.pagesRead, app.repository.totalMinutes) { completed, favorites, pages, minutes ->
                "Reading statistics\n\nCompleted books: $completed\nFavorites: $favorites\nPages read: $pages\nReading time: $minutes minutes"
            }.collect { text.text = it }
        }
    }
}

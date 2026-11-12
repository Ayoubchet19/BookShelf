package fr.estiam.traillog.bookshelf.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import fr.estiam.traillog.R
import fr.estiam.traillog.bookshelf.BookshelfApplication
import kotlinx.coroutines.launch

class BookShelfActivity : AppCompatActivity() {
    private val app get() = application as BookshelfApplication
    private val vm by viewModels<BookListViewModel> { BookListViewModel.Factory(app.repository, app.preferences) }
    private val adapter = BookPagingAdapter { startActivity(Intent(this, BookDetailActivity::class.java).putExtra("book_id", it.id)) }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_bookshelf)
        val list = findViewById<RecyclerView>(R.id.book_list)
        val search = findViewById<EditText>(R.id.search_input)
        list.layoutManager = LinearLayoutManager(this); list.adapter = adapter
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) { vm.search(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        findViewById<Button>(R.id.favorites_filter).setOnClickListener { vm.setFavorites(!vm.favoritesOnly.value) }
        findViewById<Button>(R.id.completed_filter).setOnClickListener { vm.setCompleted(!vm.completedOnly.value) }
        findViewById<Button>(R.id.categories_button).setOnClickListener { startActivity(Intent(this, CategoriesActivity::class.java)) }
        findViewById<Button>(R.id.stats_button).setOnClickListener { startActivity(Intent(this, StatisticsActivity::class.java)) }
        findViewById<Button>(R.id.settings_button).setOnClickListener { startActivity(Intent(this, BookSettingsActivity::class.java)) }
        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.add_book)
            .setOnClickListener { startActivity(Intent(this, EditBookActivity::class.java)) }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { app.repository.seedIfNeeded() }
                launch { vm.books.collect { adapter.submitData(it) } }
                launch { vm.favoritesOnly.collect { findViewById<Button>(R.id.favorites_filter).isSelected = it } }
                launch { vm.completedOnly.collect { findViewById<Button>(R.id.completed_filter).isSelected = it } }
            }
        }
    }
}

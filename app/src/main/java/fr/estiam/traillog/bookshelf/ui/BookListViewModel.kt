package fr.estiam.traillog.bookshelf.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import fr.estiam.traillog.bookshelf.data.BookRepository
import fr.estiam.traillog.bookshelf.model.Book
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

class BookListViewModel(private val repository: BookRepository, preferences: BookshelfPreferences) : ViewModel() {
    private val query = MutableStateFlow("")
    private val favorites = MutableStateFlow(false)
    private val completed = MutableStateFlow(false)
    val currentQuery = query.asStateFlow()
    val favoritesOnly = favorites.asStateFlow()
    val completedOnly = completed.asStateFlow()
    val settings = preferences.flow.stateIn(viewModelScope, SharingStarted.Eagerly, fr.estiam.traillog.bookshelf.model.UserPreferences())
    @OptIn(ExperimentalCoroutinesApi::class)
    val books: Flow<PagingData<Book>> = combine(query, favorites, completed, settings) { q, f, c, p ->
        Triple(q, f, if (p.showCompleted) c else false) to p.sort
    }.flatMapLatest { (filters, sort) -> repository.books(filters.first, filters.second, filters.third, sort) }.cachedIn(viewModelScope)
    fun search(value: String) { query.value = value }
    fun setFavorites(value: Boolean) { favorites.value = value }
    fun setCompleted(value: Boolean) { completed.value = value }
    class Factory(private val repository: BookRepository, private val preferences: BookshelfPreferences) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = BookListViewModel(repository, preferences) as T
    }
}

interface BookshelfPreferences {
    val flow: Flow<fr.estiam.traillog.bookshelf.model.UserPreferences>
    suspend fun setSort(value: String)
    suspend fun setDarkMode(value: Boolean)
    suspend fun setShowCompleted(value: Boolean)
}

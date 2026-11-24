package fr.estiam.traillog.bookshelf.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import fr.estiam.traillog.bookshelf.data.BookRepository
import fr.estiam.traillog.bookshelf.domain.CatalogueFilter
import fr.estiam.traillog.bookshelf.domain.catalogueFilter
import fr.estiam.traillog.bookshelf.model.Book
import fr.estiam.traillog.bookshelf.model.UserPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

class BookListViewModel(private val repository: BookRepository, preferences: BookshelfPreferences) : ViewModel() {
    private val query = MutableStateFlow("")
    private val favorites = MutableStateFlow(false)
    private val completed = MutableStateFlow(false)
    val currentQuery = query.asStateFlow()
    val favoritesOnly = favorites.asStateFlow()
    val completedOnly = completed.asStateFlow()
    val settings = preferences.flow.stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    /** Screen filters + stored preferences (sort, show completed). Equal values are not re-emitted. */
    val filter: StateFlow<CatalogueFilter> = combine(query, favorites, completed, settings) { q, f, c, p ->
        catalogueFilter(q, f, c, p)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CatalogueFilter())

    @OptIn(ExperimentalCoroutinesApi::class)
    val books: Flow<PagingData<Book>> = filter.flatMapLatest { f ->
        repository.books(f.query, f.favoritesOnly, f.completedOnly, f.sort, f.hideCompleted)
    }.cachedIn(viewModelScope)

    fun search(value: String) { query.value = value }
    fun setFavorites(value: Boolean) { favorites.value = value }
    fun setCompleted(value: Boolean) { completed.value = value }

    class Factory(private val repository: BookRepository, private val preferences: BookshelfPreferences) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = BookListViewModel(repository, preferences) as T
    }
}

interface BookshelfPreferences {
    val flow: Flow<UserPreferences>
    suspend fun setSort(value: String)
    suspend fun setDarkMode(value: Boolean)
    suspend fun setShowCompleted(value: Boolean)
}

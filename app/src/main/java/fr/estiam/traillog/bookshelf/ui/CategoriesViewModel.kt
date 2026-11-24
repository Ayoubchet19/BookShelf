package fr.estiam.traillog.bookshelf.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.estiam.traillog.bookshelf.data.BookRepository
import fr.estiam.traillog.bookshelf.domain.CategoryError
import fr.estiam.traillog.bookshelf.domain.CategoryValidationException
import fr.estiam.traillog.bookshelf.domain.validateCategoryName
import fr.estiam.traillog.bookshelf.model.Category
import fr.estiam.traillog.bookshelf.model.CategorySummary
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * State holder of [CategoriesActivity]: live list, the category being renamed, the name
 * error and a pending delete confirmation. Everything survives a rotation.
 */
class CategoriesViewModel(private val repository: BookRepository) : ViewModel() {

    data class ListState(val loaded: Boolean = false, val items: List<CategorySummary> = emptyList())

    /** An error shown under the name field. [bookCount] is used by IN_USE. */
    data class NameError(val error: CategoryError, val bookCount: Int = 0)

    sealed interface Event {
        data object Created : Event
        data object Renamed : Event
        data object Deleted : Event
        /** Delete refused (category still used, or already gone). */
        data class DeleteFailed(val error: CategoryError, val bookCount: Int) : Event
    }

    val state: StateFlow<ListState> = repository.categorySummaries
        .map { ListState(true, it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListState())

    private val _editing = MutableStateFlow<CategorySummary?>(null)
    /** Category loaded in the form for renaming, null when the form creates a new one. */
    val editing: StateFlow<CategorySummary?> = _editing.asStateFlow()

    private val _nameError = MutableStateFlow<NameError?>(null)
    val nameError: StateFlow<NameError?> = _nameError.asStateFlow()

    private val _pendingDelete = MutableStateFlow<CategorySummary?>(null)
    /** Category waiting for delete confirmation (dialog shown while non-null). */
    val pendingDelete: StateFlow<CategorySummary?> = _pendingDelete.asStateFlow()

    private val events = Channel<Event>(Channel.BUFFERED)
    val eventFlow: Flow<Event> = events.receiveAsFlow()

    fun startRename(category: CategorySummary) { _editing.value = category; _nameError.value = null }
    fun cancelRename() { _editing.value = null; _nameError.value = null }
    fun clearError() { _nameError.value = null }

    /** Creates a category, or renames the one being edited. */
    fun submit(name: String) {
        val editing = _editing.value
        val existing = state.value.items.map { Category(it.id, it.name) }
        validateCategoryName(name, existing, editing?.id)?.let { _nameError.value = NameError(it); return }
        viewModelScope.launch {
            try {
                if (editing == null) {
                    repository.createCategory(name)
                    events.send(Event.Created)
                } else {
                    repository.renameCategory(editing.id, name)
                    _editing.value = null
                    events.send(Event.Renamed)
                }
                _nameError.value = null
            } catch (e: CategoryValidationException) {
                _nameError.value = NameError(e.error, e.bookCount)
            }
        }
    }

    fun requestDelete(category: CategorySummary) { _pendingDelete.value = category }
    fun cancelDelete() { _pendingDelete.value = null }

    fun confirmDelete() {
        val category = _pendingDelete.value ?: return
        _pendingDelete.value = null
        viewModelScope.launch {
            try {
                repository.deleteCategory(category.id)
                if (_editing.value?.id == category.id) cancelRename()
                events.send(Event.Deleted)
            } catch (e: CategoryValidationException) {
                events.send(Event.DeleteFailed(e.error, e.bookCount))
            }
        }
    }

    class Factory(private val repository: BookRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CategoriesViewModel(repository) as T
    }
}

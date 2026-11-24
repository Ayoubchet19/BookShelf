package fr.estiam.traillog.bookshelf.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.estiam.traillog.bookshelf.data.BookRepository
import fr.estiam.traillog.bookshelf.domain.BookFormErrors
import fr.estiam.traillog.bookshelf.domain.BookFormInput
import fr.estiam.traillog.bookshelf.domain.BookFormResult
import fr.estiam.traillog.bookshelf.domain.BookFormValidationException
import fr.estiam.traillog.bookshelf.domain.validateBookForm
import fr.estiam.traillog.bookshelf.model.Book
import fr.estiam.traillog.bookshelf.model.Category
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * State holder of [EditBookActivity]. It survives rotation, so the loaded book, the picked
 * category and the field errors are kept. Typed text is restored by the EditTexts themselves.
 *
 * @param bookId null when adding a new book.
 */
class EditBookViewModel(private val repository: BookRepository, private val bookId: String?) : ViewModel() {

    data class LoadState(
        val loaded: Boolean = false,
        /** The book being edited (null when adding, or when it was deleted meanwhile). */
        val book: Book? = null,
        val missing: Boolean = false
    )

    sealed interface Event {
        data object Saved : Event
    }

    val isEditing: Boolean get() = bookId != null

    private val _load = MutableStateFlow(LoadState(loaded = bookId == null))
    val load: StateFlow<LoadState> = _load.asStateFlow()

    /** Categories for the dropdown, updated live (e.g. after one is created from "Manage categories"). */
    val categories: StateFlow<List<Category>> =
        repository.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    val selectedCategoryId: StateFlow<String?> = _selectedCategoryId.asStateFlow()

    private val _errors = MutableStateFlow(BookFormErrors())
    val errors: StateFlow<BookFormErrors> = _errors.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val events = Channel<Event>(Channel.BUFFERED)
    val eventFlow: Flow<Event> = events.receiveAsFlow()

    init {
        if (bookId != null) viewModelScope.launch {
            val book = repository.findBook(bookId)
            if (book != null) _selectedCategoryId.value = book.categoryId
            _load.value = LoadState(loaded = true, book = book, missing = book == null)
        }
    }

    fun selectCategory(id: String?) {
        if (_selectedCategoryId.value == id) return
        _selectedCategoryId.value = id
        if (id != null && _errors.value.category != null) _errors.value = _errors.value.copy(category = null)
    }

    enum class Field { TITLE, AUTHOR, PAGES, NOTES }

    /** Called when the user edits a field: its error disappears. */
    fun clearError(field: Field) {
        val e = _errors.value
        _errors.value = when (field) {
            Field.TITLE -> e.copy(title = null)
            Field.AUTHOR -> e.copy(author = null)
            Field.PAGES -> e.copy(pageCount = null)
            Field.NOTES -> e.copy(notes = null)
        }
    }

    /** Validates locally first (instant feedback), then saves through the repository (authoritative check). */
    fun save(title: String, author: String, pages: String, notes: String) {
        if (_saving.value) return
        val input = BookFormInput(title, author, pages, _selectedCategoryId.value, notes)
        val local = validateBookForm(input, _load.value.book?.pagesRead ?: 0)
        if (local is BookFormResult.Invalid) { _errors.value = local.errors; return }
        _errors.value = BookFormErrors()
        _saving.value = true
        viewModelScope.launch {
            try {
                repository.saveBookForm(bookId, input)
                events.send(Event.Saved)
            } catch (e: BookFormValidationException) {
                _errors.value = e.errors
            } finally {
                _saving.value = false
            }
        }
    }

    class Factory(private val repository: BookRepository, private val bookId: String?) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = EditBookViewModel(repository, bookId) as T
    }
}

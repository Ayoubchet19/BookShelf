package fr.estiam.traillog.bookshelf.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.estiam.traillog.bookshelf.data.BookRepository
import fr.estiam.traillog.bookshelf.domain.SessionError
import fr.estiam.traillog.bookshelf.domain.SessionValidationException
import fr.estiam.traillog.bookshelf.domain.estimatedMinutesRemaining
import fr.estiam.traillog.bookshelf.domain.pagesPerHour
import fr.estiam.traillog.bookshelf.domain.remainingPages
import fr.estiam.traillog.bookshelf.model.Book
import fr.estiam.traillog.bookshelf.model.ReadingSession
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * State holder of [BookDetailActivity]. Lives across configuration changes, so the
 * session being edited, the validation errors and a pending delete confirmation
 * survive a rotation. (EditText contents are restored by the views themselves.)
 */
class BookDetailViewModel(private val repository: BookRepository, private val bookId: String) : ViewModel() {

    data class DetailState(
        val loaded: Boolean = false,
        val book: Book? = null,
        val sessions: List<ReadingSession> = emptyList(),
        val remainingPages: Int = 0,
        /** Estimated minutes left; null when no speed is known yet. */
        val remainingMinutes: Int? = null
    )

    /** A validation problem shown under a form field (or under the form for [general]). */
    data class FormErrors(
        val pages: FormError? = null,
        val minutes: FormError? = null,
        val general: FormError? = null
    ) { val hasErrors get() = pages != null || minutes != null || general != null }

    enum class FormErrorKind { NUMBER_REQUIRED, PAGES_NOT_POSITIVE, MINUTES_NOT_POSITIVE, PAGES_EXCEED, BOOK_COMPLETED, BOOK_NOT_FOUND, SESSION_NOT_FOUND }
    data class FormError(val kind: FormErrorKind, val remainingPages: Int = 0)

    sealed interface Event {
        data object SessionSaved : Event
        /** [wasBeingEdited]: the deleted session was loaded in the form, which must be cleared. */
        data class SessionDeleted(val wasBeingEdited: Boolean) : Event
    }

    /**
     * Book, its sessions and the remaining-time estimate. The speed comes from this
     * book's sessions; when it has none, the average speed over all books is used.
     */
    val state: StateFlow<DetailState> = combine(
        repository.observeBook(bookId), repository.sessions(bookId), repository.allSessions
    ) { book, sessions, allSessions ->
        val remaining = book?.let { remainingPages(it.pageCount, it.pagesRead) } ?: 0
        val speed = pagesPerHour(sessions) ?: pagesPerHour(allSessions)
        DetailState(true, book, sessions, remaining, estimatedMinutesRemaining(remaining, speed))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailState())

    private val _editingSessionId = MutableStateFlow<String?>(null)
    /** Id of the session currently loaded in the form, or null when adding a new one. */
    val editingSessionId: StateFlow<String?> = _editingSessionId.asStateFlow()

    private val _formErrors = MutableStateFlow(FormErrors())
    val formErrors: StateFlow<FormErrors> = _formErrors.asStateFlow()

    private val _pendingDelete = MutableStateFlow<ReadingSession?>(null)
    /** Session waiting for the user to confirm its deletion (dialog shown while non-null). */
    val pendingDelete: StateFlow<ReadingSession?> = _pendingDelete.asStateFlow()

    private val events = Channel<Event>(Channel.BUFFERED)
    /** One-shot UI events (toast, clearing the form). */
    val eventFlow: Flow<Event> = events.receiveAsFlow()

    fun startEditing(session: ReadingSession) { _editingSessionId.value = session.id; _formErrors.value = FormErrors() }
    fun cancelEditing() { _editingSessionId.value = null; _formErrors.value = FormErrors() }

    /** Validates the raw form input, then adds or updates the session. */
    fun save(pagesText: String, minutesText: String) {
        val pages = pagesText.trim().toIntOrNull()
        val minutes = minutesText.trim().toIntOrNull()
        if (pages == null || minutes == null) {
            _formErrors.value = FormErrors(
                pages = if (pages == null) FormError(FormErrorKind.NUMBER_REQUIRED) else null,
                minutes = if (minutes == null) FormError(FormErrorKind.NUMBER_REQUIRED) else null
            )
            return
        }
        val editing = _editingSessionId.value
        viewModelScope.launch {
            try {
                if (editing == null) repository.addSession(bookId, pages, minutes)
                else repository.updateSession(editing, pages, minutes)
                _formErrors.value = FormErrors()
                _editingSessionId.value = null
                events.send(Event.SessionSaved)
            } catch (e: SessionValidationException) {
                _formErrors.value = e.toFormErrors()
            }
        }
    }

    fun requestDelete(session: ReadingSession) { _pendingDelete.value = session }
    fun cancelDelete() { _pendingDelete.value = null }

    fun confirmDelete() {
        val session = _pendingDelete.value ?: return
        _pendingDelete.value = null
        viewModelScope.launch {
            try {
                repository.deleteSession(session.id)
                val wasBeingEdited = _editingSessionId.value == session.id
                if (wasBeingEdited) cancelEditing()
                events.send(Event.SessionDeleted(wasBeingEdited))
            } catch (e: SessionValidationException) {
                _formErrors.value = e.toFormErrors()
            }
        }
    }

    fun toggleFavorite() {
        val book = state.value.book ?: return
        viewModelScope.launch { repository.save(book.copy(isFavorite = !book.isFavorite)) }
    }

    fun deleteBook(onDeleted: () -> Unit) {
        viewModelScope.launch { repository.delete(bookId); onDeleted() }
    }

    private fun SessionValidationException.toFormErrors(): FormErrors = when (error) {
        SessionError.PAGES_NOT_POSITIVE -> FormErrors(pages = FormError(FormErrorKind.PAGES_NOT_POSITIVE))
        SessionError.MINUTES_NOT_POSITIVE -> FormErrors(minutes = FormError(FormErrorKind.MINUTES_NOT_POSITIVE))
        SessionError.PAGES_EXCEED_REMAINING -> FormErrors(
            pages = FormError(if (remainingPages == 0) FormErrorKind.BOOK_COMPLETED else FormErrorKind.PAGES_EXCEED, remainingPages)
        )
        SessionError.BOOK_NOT_FOUND -> FormErrors(general = FormError(FormErrorKind.BOOK_NOT_FOUND))
        SessionError.SESSION_NOT_FOUND -> FormErrors(general = FormError(FormErrorKind.SESSION_NOT_FOUND))
    }

    class Factory(private val repository: BookRepository, private val bookId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = BookDetailViewModel(repository, bookId) as T
    }
}

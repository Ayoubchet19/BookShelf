package fr.estiam.traillog.bookshelf.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import fr.estiam.traillog.R
import fr.estiam.traillog.bookshelf.BookshelfApplication
import fr.estiam.traillog.bookshelf.model.ReadingSession
import fr.estiam.traillog.bookshelf.ui.BookDetailViewModel.FormError
import fr.estiam.traillog.bookshelf.ui.BookDetailViewModel.FormErrorKind
import fr.estiam.traillog.databinding.ActivityBookDetailBinding
import kotlinx.coroutines.launch

/**
 * Book detail: progress, estimated time left, reading-session form (add / edit) and
 * the list of sessions with edit and delete (with confirmation).
 * Expects the book id in the "book_id" extra ([EXTRA_BOOK_ID]).
 */
class BookDetailActivity : AppCompatActivity() {
    private val app get() = application as BookshelfApplication
    private lateinit var binding: ActivityBookDetailBinding
    private lateinit var bookId: String
    private val vm by viewModels<BookDetailViewModel> { BookDetailViewModel.Factory(app.repository, bookId) }
    private val adapter = SessionAdapter(onEdit = ::editSession, onDelete = { vm.requestDelete(it) })
    private var deleteDialog: AlertDialog? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        bookId = intent.getStringExtra(EXTRA_BOOK_ID) ?: return finish()
        binding = ActivityBookDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.sessionList.layoutManager = LinearLayoutManager(this)
        binding.sessionList.adapter = adapter

        binding.favoriteButton.setOnClickListener { vm.toggleFavorite() }
        binding.editBookButton.setOnClickListener { startActivity(Intent(this, EditBookActivity::class.java).putExtra(EXTRA_BOOK_ID, bookId)) }
        binding.deleteBookButton.setOnClickListener { vm.deleteBook { finish() } }
        binding.saveSessionButton.setOnClickListener {
            vm.save(binding.sessionPagesInput.text?.toString().orEmpty(), binding.sessionMinutesInput.text?.toString().orEmpty())
        }
        binding.cancelEditButton.setOnClickListener { vm.cancelEditing(); clearForm() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.state.collect(::render) }
                launch { vm.formErrors.collect(::renderErrors) }
                launch { vm.editingSessionId.collect(::renderEditing) }
                launch { vm.pendingDelete.collect(::renderDeleteDialog) }
                launch {
                    vm.eventFlow.collect { event ->
                        val message = when (event) {
                            BookDetailViewModel.Event.SessionSaved -> { clearForm(); R.string.session_saved }
                            is BookDetailViewModel.Event.SessionDeleted -> { if (event.wasBeingEdited) clearForm(); R.string.session_deleted }
                        }
                        Toast.makeText(this@BookDetailActivity, message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        // Dismiss without cancelling: the pending delete stays in the ViewModel and the
        // dialog is shown again after a rotation.
        deleteDialog?.setOnCancelListener(null)
        deleteDialog?.dismiss()
        deleteDialog = null
        super.onDestroy()
    }

    private fun render(state: BookDetailViewModel.DetailState) {
        if (!state.loaded) return
        val book = state.book
        if (book == null) {
            binding.bookTitle.setText(R.string.book_not_found)
            listOf(binding.bookAuthor, binding.bookCategory, binding.bookProgress, binding.bookStatus, binding.remainingTime, binding.bookNotes)
                .forEach { it.text = "" }
            return
        }
        binding.bookTitle.text = book.title + if (book.isFavorite) " ★" else ""
        binding.bookAuthor.text = book.author
        binding.bookCategory.text = book.categoryName
        binding.bookProgress.text = getString(R.string.book_progress, book.progress, book.pagesRead, book.pageCount)
        binding.bookStatus.setText(if (book.isCompleted) R.string.book_completed_label else R.string.book_in_progress_label)
        binding.remainingTime.text = when {
            state.remainingPages == 0 -> getString(R.string.remaining_time_done)
            state.remainingMinutes == null -> getString(R.string.remaining_time_unknown)
            else -> getString(R.string.remaining_time, formatDuration(this, state.remainingMinutes), state.remainingPages)
        }
        binding.bookNotes.text = book.notes
        binding.bookNotes.visibility = if (book.notes.isBlank()) View.GONE else View.VISIBLE

        adapter.submitList(state.sessions)
        binding.sessionsEmpty.visibility = if (state.sessions.isEmpty()) View.VISIBLE else View.GONE
        binding.sessionList.visibility = if (state.sessions.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun renderErrors(errors: BookDetailViewModel.FormErrors) {
        binding.sessionPagesLayout.error = errors.pages?.let(::message)
        binding.sessionMinutesLayout.error = errors.minutes?.let(::message)
        binding.sessionFormError.text = errors.general?.let(::message)
        binding.sessionFormError.visibility = if (errors.general == null) View.GONE else View.VISIBLE
    }

    private fun renderEditing(editingId: String?) {
        val editing = editingId != null
        binding.sessionFormTitle.setText(if (editing) R.string.session_form_edit_title else R.string.session_form_title)
        binding.saveSessionButton.setText(if (editing) R.string.session_update else R.string.session_save)
        binding.cancelEditButton.visibility = if (editing) View.VISIBLE else View.GONE
    }

    private fun renderDeleteDialog(session: ReadingSession?) {
        if (session == null) { deleteDialog?.dismiss(); deleteDialog = null; return }
        if (deleteDialog?.isShowing == true) return
        deleteDialog = AlertDialog.Builder(this)
            .setTitle(R.string.session_delete_title)
            .setMessage(getString(R.string.session_delete_message, session.pagesRead, session.durationMinutes, SessionAdapter.formatSessionDate(session.startedAt)))
            .setPositiveButton(R.string.session_delete) { _, _ -> vm.confirmDelete() }
            .setNegativeButton(R.string.session_cancel_edit) { _, _ -> vm.cancelDelete() }
            .setOnCancelListener { vm.cancelDelete() }
            .show()
    }

    private fun editSession(session: ReadingSession) {
        vm.startEditing(session)
        binding.sessionPagesInput.setText(session.pagesRead.toString())
        binding.sessionMinutesInput.setText(session.durationMinutes.toString())
        binding.sessionPagesInput.requestFocus()
        binding.detailScroll.smoothScrollTo(0, binding.sessionFormTitle.top)
    }

    private fun clearForm() {
        binding.sessionPagesInput.text = null
        binding.sessionMinutesInput.text = null
    }

    private fun message(error: FormError): String = when (error.kind) {
        FormErrorKind.NUMBER_REQUIRED -> getString(R.string.error_number_required)
        FormErrorKind.PAGES_NOT_POSITIVE -> getString(R.string.error_pages_positive)
        FormErrorKind.MINUTES_NOT_POSITIVE -> getString(R.string.error_minutes_positive)
        FormErrorKind.PAGES_EXCEED -> getString(R.string.error_pages_exceed, error.remainingPages)
        FormErrorKind.BOOK_COMPLETED -> getString(R.string.error_book_completed)
        FormErrorKind.BOOK_NOT_FOUND -> getString(R.string.error_book_not_found)
        FormErrorKind.SESSION_NOT_FOUND -> getString(R.string.error_session_not_found)
    }

    companion object {
        const val EXTRA_BOOK_ID = "book_id"

        /** "1 h 05 min" or "45 min". */
        fun formatDuration(context: Context, minutes: Int): String =
            if (minutes >= 60) context.getString(R.string.duration_hours_minutes, minutes / 60, minutes % 60)
            else context.getString(R.string.duration_minutes, minutes)
    }
}

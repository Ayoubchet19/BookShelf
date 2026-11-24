package fr.estiam.traillog.bookshelf.ui

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import fr.estiam.traillog.R
import fr.estiam.traillog.bookshelf.BookshelfApplication
import fr.estiam.traillog.bookshelf.domain.CATEGORY_NAME_MAX_LENGTH
import fr.estiam.traillog.bookshelf.domain.CATEGORY_NAME_MIN_LENGTH
import fr.estiam.traillog.bookshelf.domain.CategoryError
import fr.estiam.traillog.bookshelf.model.CategorySummary
import fr.estiam.traillog.databinding.ActivityCategoriesBinding
import kotlinx.coroutines.launch

/**
 * Categories CRUD: create, list (with book count), rename and delete (with confirmation).
 * A category used by books cannot be deleted. State lives in [CategoriesViewModel].
 */
class CategoriesActivity : AppCompatActivity() {
    private val app get() = application as BookshelfApplication
    private val vm by viewModels<CategoriesViewModel> { CategoriesViewModel.Factory(app.repository) }
    private lateinit var binding: ActivityCategoriesBinding
    private val adapter = CategoryAdapter(onEdit = ::startRename, onDelete = { vm.requestDelete(it) })
    private var deleteDialog: AlertDialog? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        binding = ActivityCategoriesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.categoryList.layoutManager = LinearLayoutManager(this)
        binding.categoryList.adapter = adapter

        binding.saveCategoryButton.setOnClickListener { submit() }
        binding.categoryNameInput.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) { submit(); true } else false
        }
        binding.cancelRenameButton.setOnClickListener { vm.cancelRename(); binding.categoryNameInput.text?.clear() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.state.collect(::renderList) }
                launch { vm.editing.collect(::renderEditing) }
                launch { vm.nameError.collect(::renderError) }
                launch { vm.pendingDelete.collect(::renderDeleteDialog) }
                launch { vm.eventFlow.collect(::onEvent) }
            }
        }
    }

    /** Registered after onRestoreInstanceState so restoring the text does not wipe the error (see EditBookActivity). */
    override fun onPostCreate(state: Bundle?) {
        super.onPostCreate(state)
        binding.categoryNameInput.doAfterTextChanged { vm.clearError() }
    }

    override fun onDestroy() {
        // Dismiss without cancelling: the pending delete stays in the ViewModel, so the
        // dialog is shown again after a rotation.
        deleteDialog?.setOnCancelListener(null)
        deleteDialog?.dismiss()
        deleteDialog = null
        super.onDestroy()
    }

    private fun submit() = vm.submit(binding.categoryNameInput.text?.toString().orEmpty())

    private fun startRename(category: CategorySummary) {
        vm.startRename(category)
        binding.categoryNameInput.setText(category.name)
        binding.categoryNameInput.setSelection(category.name.length)
        binding.categoryNameInput.requestFocus()
    }

    private fun renderList(state: CategoriesViewModel.ListState) {
        adapter.submitList(state.items)
        binding.categoriesEmpty.visibility = if (state.loaded && state.items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun renderEditing(editing: CategorySummary?) {
        binding.saveCategoryButton.setText(if (editing == null) R.string.category_add else R.string.category_rename)
        binding.categoryNameLayout.hint = getString(if (editing == null) R.string.category_new_hint else R.string.category_rename_hint)
        binding.cancelRenameButton.visibility = if (editing == null) View.GONE else View.VISIBLE
        binding.editingLabel.visibility = if (editing == null) View.GONE else View.VISIBLE
        binding.editingLabel.text = editing?.let { getString(R.string.category_editing, it.name) }
    }

    private fun renderError(error: CategoriesViewModel.NameError?) {
        binding.categoryNameLayout.error = error?.let { message(it.error, it.bookCount) }
    }

    private fun renderDeleteDialog(category: CategorySummary?) {
        if (category == null) { deleteDialog?.dismiss(); deleteDialog = null; return }
        if (deleteDialog?.isShowing == true) return
        deleteDialog = AlertDialog.Builder(this)
            .setTitle(R.string.category_delete_title)
            .setMessage(getString(R.string.category_delete_message, category.name))
            .setPositiveButton(R.string.category_delete) { _, _ -> vm.confirmDelete() }
            .setNegativeButton(R.string.category_cancel) { _, _ -> vm.cancelDelete() }
            .setOnCancelListener { vm.cancelDelete() }
            .show()
    }

    private fun onEvent(event: CategoriesViewModel.Event) {
        val text = when (event) {
            CategoriesViewModel.Event.Created -> { binding.categoryNameInput.text?.clear(); getString(R.string.category_created) }
            CategoriesViewModel.Event.Renamed -> { binding.categoryNameInput.text?.clear(); getString(R.string.category_renamed) }
            CategoriesViewModel.Event.Deleted -> getString(R.string.category_deleted)
            is CategoriesViewModel.Event.DeleteFailed -> message(event.error, event.bookCount)
        }
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    private fun message(error: CategoryError, bookCount: Int): String = when (error) {
        CategoryError.NAME_REQUIRED -> getString(R.string.category_error_required)
        CategoryError.NAME_TOO_SHORT -> getString(R.string.category_error_too_short, CATEGORY_NAME_MIN_LENGTH)
        CategoryError.NAME_TOO_LONG -> getString(R.string.category_error_too_long, CATEGORY_NAME_MAX_LENGTH)
        CategoryError.DUPLICATE_NAME -> getString(R.string.category_error_duplicate)
        CategoryError.NOT_FOUND -> getString(R.string.category_error_not_found)
        CategoryError.IN_USE -> resources.getQuantityString(R.plurals.category_error_in_use, bookCount, bookCount)
    }
}

package fr.estiam.traillog.bookshelf.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import fr.estiam.traillog.R
import fr.estiam.traillog.bookshelf.BookshelfApplication
import fr.estiam.traillog.bookshelf.domain.AUTHOR_MAX_LENGTH
import fr.estiam.traillog.bookshelf.domain.BookFormError
import fr.estiam.traillog.bookshelf.domain.BookFormErrors
import fr.estiam.traillog.bookshelf.domain.NOTES_MAX_LENGTH
import fr.estiam.traillog.bookshelf.domain.PAGE_COUNT_MAX
import fr.estiam.traillog.bookshelf.domain.TITLE_MAX_LENGTH
import fr.estiam.traillog.bookshelf.model.Category
import fr.estiam.traillog.databinding.ActivityEditBookBinding
import kotlinx.coroutines.launch

/**
 * Add / edit book form with per-field validation (domain/BookForm.kt).
 * Pass [EXTRA_BOOK_ID] to edit an existing book; without it a new book is created.
 * The category is picked from the existing categories ("Manage categories" opens
 * [CategoriesActivity]; the dropdown updates by itself when one is added).
 */
class EditBookActivity : AppCompatActivity() {
    private val app get() = application as BookshelfApplication
    private var bookId: String? = null
    private val vm by viewModels<EditBookViewModel> { EditBookViewModel.Factory(app.repository, bookId) }
    private lateinit var binding: ActivityEditBookBinding
    private lateinit var categoryAdapter: ArrayAdapter<String>
    private var categories: List<Category> = emptyList()
    /** True once the fields were filled from the loaded book; saved so a rotation keeps the user's edits. */
    private var fieldsFilled = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        bookId = intent.getStringExtra(EXTRA_BOOK_ID)
        fieldsFilled = state?.getBoolean(STATE_FILLED) ?: false
        binding = ActivityEditBookBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.formHeading.setText(if (vm.isEditing) R.string.form_title_edit else R.string.form_title_add)
        title = binding.formHeading.text

        categoryAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, mutableListOf<String>())
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.categorySpinner.adapter = categoryAdapter
        binding.categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // The spinner auto-selects item 0 when filled: ignore it until the edited book is loaded.
                if (vm.isEditing && !vm.load.value.loaded) return
                vm.selectCategory(categories.getOrNull(position)?.id)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        binding.manageCategoriesButton.setOnClickListener { startActivity(Intent(this, CategoriesActivity::class.java)) }
        binding.cancelButton.setOnClickListener { finish() }
        binding.saveButton.setOnClickListener {
            vm.save(
                binding.titleInput.text?.toString().orEmpty(),
                binding.authorInput.text?.toString().orEmpty(),
                binding.pagesInput.text?.toString().orEmpty(),
                binding.notesInput.text?.toString().orEmpty()
            )
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.load.collect(::renderLoad) }
                launch { vm.categories.collect(::renderCategories) }
                launch { vm.selectedCategoryId.collect { selectSpinner(it) } }
                launch { vm.errors.collect(::renderErrors) }
                launch { vm.saving.collect { binding.saveButton.isEnabled = !it } }
                launch {
                    vm.eventFlow.collect {
                        Toast.makeText(this@EditBookActivity, R.string.form_saved, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
        }
    }

    /**
     * Clear a field's error as soon as the user edits it. Registered here, not in onCreate:
     * onRestoreInstanceState (between onStart and onPostCreate) puts the typed text back,
     * which would fire the listeners and wipe the errors restored after a rotation (docs/BUGS.md, bug 3).
     */
    override fun onPostCreate(state: Bundle?) {
        super.onPostCreate(state)
        binding.titleInput.doAfterTextChanged { vm.clearError(EditBookViewModel.Field.TITLE) }
        binding.authorInput.doAfterTextChanged { vm.clearError(EditBookViewModel.Field.AUTHOR) }
        binding.pagesInput.doAfterTextChanged { vm.clearError(EditBookViewModel.Field.PAGES) }
        binding.notesInput.doAfterTextChanged { vm.clearError(EditBookViewModel.Field.NOTES) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_FILLED, fieldsFilled)
    }

    private fun renderLoad(load: EditBookViewModel.LoadState) {
        if (!load.loaded) return
        if (load.missing) {
            Toast.makeText(this, R.string.form_book_missing, Toast.LENGTH_LONG).show()
            finish(); return
        }
        val book = load.book ?: return
        if (book.pagesRead > 0) {
            binding.pagesReadInfo.visibility = View.VISIBLE
            binding.pagesReadInfo.text = getString(R.string.form_pages_read_info, book.pagesRead)
        }
        if (fieldsFilled) return
        binding.titleInput.setText(book.title)
        binding.authorInput.setText(book.author)
        binding.pagesInput.setText(book.pageCount.toString())
        binding.notesInput.setText(book.notes)
        fieldsFilled = true
    }

    private fun renderCategories(list: List<Category>) {
        categories = list
        categoryAdapter.clear()
        categoryAdapter.addAll(list.map { it.name })
        binding.categoryEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        // Default to the first category for a new book; keep the current choice otherwise.
        val selected = vm.selectedCategoryId.value
        if ((selected == null || list.none { it.id == selected }) && list.isNotEmpty() && !vm.isEditing) {
            vm.selectCategory(list.first().id)
        }
        selectSpinner(vm.selectedCategoryId.value)
    }

    private fun selectSpinner(categoryId: String?) {
        val index = categories.indexOfFirst { it.id == categoryId }
        if (index >= 0 && binding.categorySpinner.selectedItemPosition != index) binding.categorySpinner.setSelection(index)
    }

    private fun renderErrors(errors: BookFormErrors) {
        binding.titleLayout.error = errors.title?.let(::message)
        binding.authorLayout.error = errors.author?.let(::message)
        binding.pagesLayout.error = errors.pageCount?.let(::message)
        binding.notesLayout.error = errors.notes?.let(::message)
        val categoryError = errors.category?.let(::message)
        binding.categoryError.text = categoryError
        binding.categoryError.visibility = if (categoryError == null) View.GONE else View.VISIBLE
    }

    private fun message(error: BookFormError): String = when (error) {
        BookFormError.TITLE_REQUIRED -> getString(R.string.form_error_title_required)
        BookFormError.TITLE_TOO_LONG -> getString(R.string.form_error_title_too_long, TITLE_MAX_LENGTH)
        BookFormError.AUTHOR_REQUIRED -> getString(R.string.form_error_author_required)
        BookFormError.AUTHOR_TOO_LONG -> getString(R.string.form_error_author_too_long, AUTHOR_MAX_LENGTH)
        BookFormError.PAGES_REQUIRED -> getString(R.string.form_error_pages_required)
        BookFormError.PAGES_NOT_A_NUMBER -> getString(R.string.form_error_pages_number)
        BookFormError.PAGES_OUT_OF_RANGE -> getString(R.string.form_error_pages_range, PAGE_COUNT_MAX)
        BookFormError.PAGES_BELOW_PAGES_READ -> getString(R.string.form_error_pages_below_read, vm.load.value.book?.pagesRead ?: 0)
        BookFormError.CATEGORY_REQUIRED -> getString(R.string.form_error_category_required)
        BookFormError.CATEGORY_NOT_FOUND -> getString(R.string.form_error_category_missing)
        BookFormError.NOTES_TOO_LONG -> getString(R.string.form_error_notes_too_long, NOTES_MAX_LENGTH)
    }

    companion object {
        const val EXTRA_BOOK_ID = "book_id"
        private const val STATE_FILLED = "fields_filled"
    }
}

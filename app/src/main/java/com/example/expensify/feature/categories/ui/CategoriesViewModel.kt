package com.example.expensify.feature.categories.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensify.R
import com.example.expensify.core.error.ErrorMessages
import com.example.expensify.core.ui.Messages
import com.example.expensify.core.ui.UiText
import com.example.expensify.core.ui.plain
import com.example.expensify.core.ui.res
import com.example.expensify.feature.categories.data.CategoryRepository
import com.example.expensify.feature.categories.domain.Category
import com.example.expensify.feature.categories.domain.CategoryRules
import com.example.expensify.feature.categories.domain.CategoryType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CategoriesUiState {
    data object Loading : CategoriesUiState
    data class Content(val expense: CategoryRules.Lists, val income: CategoryRules.Lists) : CategoriesUiState {
        val isEmpty: Boolean
            get() = expense.active.isEmpty() && expense.archived.isEmpty() && income.active.isEmpty() && income.archived.isEmpty()

        fun of(type: CategoryType): CategoryRules.Lists = if (type == CategoryType.Expense) expense else income
    }
    data class Error(val message: UiText) : CategoriesUiState
}

/** The delete confirmation, and whether a write is in flight. */
data class CategoryDialogs(
    val deleteTarget: Category? = null,
    /** Buttons disable and dialogs stay while a write runs. */
    val busy: Boolean = false,
)

/**
 * `/categories` (req CAT-01…CAT-07), with the flows of category.actions.tsx:
 * archive and restore at once, delete after a confirmation, and "Add default
 * categories". A failed write reports and keeps the dialog open for another try.
 */
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val repository: CategoryRepository,
    private val messages: Messages,
) : ViewModel() {

    val state: StateFlow<CategoriesUiState> = repository.categories()
        .map<List<Category>, CategoriesUiState> { all ->
            CategoriesUiState.Content(
                expense = CategoryRules.lists(all, CategoryType.Expense),
                income = CategoryRules.lists(all, CategoryType.Income),
            )
        }
        .catch { emit(CategoriesUiState.Error(plain(ErrorMessages.of(it)))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState.Loading)

    private val _dialogs = MutableStateFlow(CategoryDialogs())
    val dialogs: StateFlow<CategoryDialogs> = _dialogs.asStateFlow()

    /** Archives at once: the category leaves pickers, and entries that use it keep it. */
    fun archive(category: Category) = attempt(R.string.could_not_archive_category) {
        repository.setArchived(category.id, archived = true)
        messages.show(res(R.string.category_archived, category.name))
    }

    fun restore(category: Category) = attempt(R.string.could_not_restore_category) {
        repository.setArchived(category.id, archived = false)
        messages.show(res(R.string.category_restored, category.name))
    }

    fun requestDelete(category: Category) {
        _dialogs.update { it.copy(deleteTarget = category) }
    }

    fun confirmDelete() {
        val target = _dialogs.value.deleteTarget ?: return
        attempt(R.string.could_not_delete_category) {
            repository.delete(target.id)
            messages.show(res(R.string.category_deleted, target.name))
        }
    }

    /** Adds the usual categories for someone starting with an empty list, or says there is nothing to add. */
    fun addDefaults() = attempt(R.string.could_not_add_defaults) {
        val added = repository.addDefaults()
        messages.show(res(if (added > 0) R.string.defaults_added else R.string.defaults_nothing_to_add))
    }

    fun dismissDialogs() {
        if (!_dialogs.value.busy) _dialogs.value = CategoryDialogs()
    }

    /** A write behind a dialog or a button: success closes every dialog; failure reports and leaves it open. */
    private fun attempt(@StringRes failure: Int, block: suspend () -> Unit) {
        viewModelScope.launch {
            _dialogs.update { it.copy(busy = true) }
            try {
                block()
                _dialogs.value = CategoryDialogs()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _dialogs.update { it.copy(busy = false) }
                messages.show(res(failure, ErrorMessages.of(error)))
            }
        }
    }
}

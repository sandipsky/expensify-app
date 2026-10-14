package com.example.expensify.feature.categories.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.expensify.R
import com.example.expensify.core.error.AppError
import com.example.expensify.core.error.ErrorMessages
import com.example.expensify.core.ui.Messages
import com.example.expensify.core.ui.UiText
import com.example.expensify.core.ui.plain
import com.example.expensify.core.ui.res
import com.example.expensify.feature.categories.data.CategoryRepository
import com.example.expensify.feature.categories.domain.Category
import com.example.expensify.feature.categories.domain.CategoryBody
import com.example.expensify.feature.categories.domain.CategoryRules
import com.example.expensify.feature.categories.domain.CategoryType
import com.example.expensify.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CategoryFormUiState(
    /** Loading the category to edit, or the list a new one's color is picked from. */
    val loading: Boolean = true,
    val editing: Category? = null,
    val type: CategoryType = CategoryType.Expense,
    val name: String = "",
    val icon: String = CategoryRules.DEFAULT_ICON,
    /** Picked once when the form opens, so the preview shows the color the category will get. */
    val color: String = CategoryRules.COLORS[0],
    val nameError: UiText? = null,
    /** A refused save. */
    val error: UiText? = null,
    val saving: Boolean = false,
) {
    val isEdit: Boolean get() = editing != null
}

sealed interface CategoryFormEvent {
    data object Saved : CategoryFormEvent
}

/**
 * Add or edit a category (req CAT-02, CAT-03): type, name and icon. The type
 * is chosen only when adding; after that it's fixed, since entries of that
 * type use it. There is no color picker: a new category gets a random palette
 * color, which it keeps.
 */
@HiltViewModel
class CategoryFormViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: CategoryRepository,
    private val messages: Messages,
) : ViewModel() {

    private val route = savedState.toRoute<Routes.CategoryForm>()

    private val _state = MutableStateFlow(CategoryFormUiState(type = CategoryType.fromStored(route.type)))
    val state: StateFlow<CategoryFormUiState> = _state.asStateFlow()

    private val _events = Channel<CategoryFormEvent>(Channel.BUFFERED)
    val events: Flow<CategoryFormEvent> = _events.receiveAsFlow()

    /** The other categories, for the unique-name check; kept current while the form is open. */
    private var known: List<Category> = emptyList()

    init {
        viewModelScope.launch {
            repository.categories().catch { }.collect { known = it }
        }
        viewModelScope.launch {
            try {
                val categories = repository.categories().first()
                val id = route.id
                if (id == null) {
                    val type = _state.value.type
                    _state.update { it.copy(loading = false, color = CategoryRules.randomColor(categories, type)) }
                } else {
                    val category = categories.firstOrNull { it.id == id } ?: repository.get(id)
                    _state.update {
                        it.copy(
                            loading = false,
                            editing = category,
                            type = category.type,
                            name = category.name,
                            icon = category.icon,
                            color = category.color,
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(loading = false, error = plain(ErrorMessages.of(error))) }
            }
        }
    }

    fun onTypeChange(value: CategoryType) {
        if (_state.value.isEdit) return
        _state.update { it.copy(type = value, nameError = null) }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value, nameError = null) }
    fun onIconChange(value: String) = _state.update { it.copy(icon = value) }

    fun save() {
        val s = _state.value
        if (s.saving || s.loading) return

        val name = s.name.trim()
        val nameError = when {
            name.isEmpty() -> res(R.string.category_form_error_name_required)
            name.length > CategoryRules.MAX_NAME_LENGTH -> res(R.string.category_form_error_name_long)
            CategoryRules.isNameTaken(known, name, s.type, s.editing?.id) -> res(s.type.nameTakenRes())
            else -> null
        }
        if (nameError != null) {
            _state.update { it.copy(nameError = nameError) }
            return
        }

        val body = CategoryBody(name = name, type = s.type, icon = s.icon, color = s.color)
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            try {
                val editing = s.editing
                val saved = if (editing == null) repository.create(body) else repository.update(editing.id, body)
                messages.show(res(if (editing == null) R.string.category_added else R.string.category_updated, saved.name))
                _events.send(CategoryFormEvent.Saved)
            } catch (error: CancellationException) {
                throw error
            } catch (error: AppError.Taken) {
                // Another device took the name since the list was read.
                _state.update { it.copy(saving = false, nameError = plain(error.message ?: "")) }
            } catch (error: Exception) {
                _state.update { it.copy(saving = false, error = plain(ErrorMessages.of(error))) }
            }
        }
    }
}

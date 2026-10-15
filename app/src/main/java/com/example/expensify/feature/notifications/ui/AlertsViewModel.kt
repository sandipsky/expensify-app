package com.example.expensify.feature.notifications.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensify.core.ui.UiText
import com.example.expensify.feature.notifications.data.AlertStore
import com.example.expensify.feature.notifications.domain.AppAlert
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One alert as the list draws it. */
data class AlertRowUi(
    val alert: AppAlert,
    /** It was new as the screen opened, and stays marked until the screen closes. */
    val fresh: Boolean,
    /** "3 minutes ago", counted from when the screen opened. */
    val timeAgo: UiText,
)

sealed interface AlertsUiState {
    data object Loading : AlertsUiState
    data class Content(val rows: List<AlertRowUi>) : AlertsUiState {
        val isEmpty: Boolean get() = rows.isEmpty()
    }
}

/**
 * The list of recent alerts behind the bell (port of AlertsPanel.tsx, req
 * NTF-06). Opening it marks every alert read; the ones that were new keep
 * their dot until it closes. An alert can be removed, or all cleared.
 */
@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val store: AlertStore,
    clock: Clock,
) : ViewModel() {

    /** The ids that were unread as the screen opened; null until the list was read once. */
    private val fresh = MutableStateFlow<Set<String>?>(null)
    private val openedAt = clock.millis()

    init {
        viewModelScope.launch {
            val opened = store.alerts.first()
            fresh.value = opened.filter { !it.read }.map { it.id }.toSet()
            store.markAllRead()
        }
    }

    val state: StateFlow<AlertsUiState> = combine(store.alerts, fresh) { alerts, fresh ->
        if (fresh == null) {
            AlertsUiState.Loading
        } else {
            AlertsUiState.Content(alerts.map { AlertRowUi(it, fresh = it.id in fresh, timeAgo = AlertLabels.timeAgo(it.at, openedAt)) })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlertsUiState.Loading)

    fun remove(id: String) {
        viewModelScope.launch { store.remove(id) }
    }

    fun clear() {
        viewModelScope.launch { store.clear() }
    }
}

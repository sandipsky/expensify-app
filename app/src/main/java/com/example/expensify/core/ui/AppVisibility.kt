package com.example.expensify.core.ui

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the app is on screen, as the web reads `document.visibilityState`:
 * an alert raised while it's in the background goes out as a system
 * notification, one raised while it's open shows inside the app (req NTF-05).
 * The one activity reports its starts and stops.
 */
@Singleton
class AppVisibility @Inject constructor() {
    private val state = MutableStateFlow(false)

    val foreground: StateFlow<Boolean> = state

    fun onStart() {
        state.value = true
    }

    fun onStop() {
        state.value = false
    }
}

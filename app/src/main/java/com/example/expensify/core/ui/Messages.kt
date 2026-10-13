package com.example.expensify.core.ui

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * App-wide snackbar messages. A form's ViewModel raises "Account added" just
 * before the form pops; the scaffold underneath shows it, so the message
 * survives navigation.
 */
@Singleton
class Messages @Inject constructor() {
    private val queue = MutableSharedFlow<UiText>(extraBufferCapacity = 16)

    val messages: SharedFlow<UiText> = queue

    fun show(text: UiText) {
        queue.tryEmit(text)
    }
}

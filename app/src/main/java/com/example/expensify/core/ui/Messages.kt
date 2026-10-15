package com.example.expensify.core.ui

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** A button on a snackbar, such as "View" on an alert. */
data class MessageAction(val label: UiText, val onClick: () -> Unit)

/**
 * One snackbar. A save confirmation is short; an alert is news, not
 * confirmation, so it stays up longer (the web's 8-second toast) and can
 * carry a way to open what it's about.
 */
data class AppMessage(
    val text: UiText,
    val long: Boolean = false,
    val action: MessageAction? = null,
)

/**
 * App-wide snackbar messages. A form's ViewModel raises "Account added" just
 * before the form pops; the scaffold underneath shows it, so the message
 * survives navigation.
 */
@Singleton
class Messages @Inject constructor() {
    private val queue = MutableSharedFlow<AppMessage>(extraBufferCapacity = 16)

    val messages: SharedFlow<AppMessage> = queue

    fun show(text: UiText) = show(AppMessage(text))

    fun show(message: AppMessage) {
        queue.tryEmit(message)
    }
}

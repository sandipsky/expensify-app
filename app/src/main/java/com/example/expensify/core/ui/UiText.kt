package com.example.expensify.core.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** Text a ViewModel hands to the screen: a resource with arguments, or a sentence already written. */
sealed interface UiText {
    /** Arguments may themselves be `UiText`, resolved first: "%1$s · %2$s" of two resources. */
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Plain(val text: String) : UiText

    fun asString(context: Context): String = when (this) {
        is Res -> context.getString(id, *args.map { if (it is UiText) it.asString(context) else it }.toTypedArray())
        is Plain -> text
    }

    @Composable
    fun asString(): String = asString(LocalContext.current)
}

fun res(@StringRes id: Int, vararg args: Any): UiText = UiText.Res(id, args.toList())
fun plain(text: String): UiText = UiText.Plain(text)

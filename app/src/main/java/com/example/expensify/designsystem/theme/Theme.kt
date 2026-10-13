package com.example.expensify.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

@Composable
fun ExpensifyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val colors = if (darkTheme) DarkExpensifyColors else LightExpensifyColors
    CompositionLocalProvider(LocalExpensifyColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = ExpensifyShapes,
            typography = ExpensifyTypography,
            content = content,
        )
    }
}

/** `ExpensifyTheme.colors.income`, beside `MaterialTheme.colorScheme`. */
object ExpensifyTheme {
    val colors: ExpensifyColors
        @Composable
        @ReadOnlyComposable
        get() = LocalExpensifyColors.current
}

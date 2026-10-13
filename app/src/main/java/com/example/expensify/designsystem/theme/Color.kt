package com.example.expensify.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

// The tokens of ../expensify/src/styles/_colors.scss, so phone and web read
// as one product. Dynamic color is off on purpose: green must mean income
// and red expense on every phone.

val LightColors = lightColorScheme(
    primary = Color(0xFF2456E6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEBEFFC),
    onPrimaryContainer = Color(0xFF1C43B4),
    secondary = Color(0xFF1C43B4),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEBEFFC),
    onSecondaryContainer = Color(0xFF1C43B4),
    tertiary = Color(0xFF0D9488),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE6F0FC),
    onTertiaryContainer = Color(0xFF0B6B62),
    background = Color(0xFFF7F7F7),
    onBackground = Color(0xFF07090F),
    surface = Color(0xFFFEFEFE),
    onSurface = Color(0xFF07090F),
    surfaceVariant = Color(0xFFEBEDEB),
    onSurfaceVariant = Color(0xFF555755),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F7F5),
    surfaceContainer = Color(0xFFF1F2F0),
    surfaceContainerHigh = Color(0xFFEBEDEB),
    surfaceContainerHighest = Color(0xFFE5E7EB),
    surfaceTint = Color(0xFF2456E6),
    outline = Color(0xFF979996),
    outlineVariant = Color(0xFFE5E7EB),
    error = Color(0xFFFF4530),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFECEA),
    onErrorContainer = Color(0xFFB3301F),
    inverseSurface = Color(0xFF1B1C1F),
    inverseOnSurface = Color(0xFFE9EBEE),
    inversePrimary = Color(0xFF93ADF5),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFF3A68E9),
    onPrimary = Color(0xFFF9FAFB),
    primaryContainer = Color(0xFF1A2340),
    onPrimaryContainer = Color(0xFF93ADF5),
    secondary = Color(0xFF93ADF5),
    onSecondary = Color(0xFF0B1F5C),
    secondaryContainer = Color(0xFF1A2340),
    onSecondaryContainer = Color(0xFF93ADF5),
    tertiary = Color(0xFF2EC4DF),
    onTertiary = Color(0xFF062A31),
    tertiaryContainer = Color(0xFF11282D),
    onTertiaryContainer = Color(0xFF2EC4DF),
    background = Color(0xFF131416),
    onBackground = Color(0xFFE9EBEE),
    surface = Color(0xFF1B1C1F),
    onSurface = Color(0xFFE9EBEE),
    surfaceVariant = Color(0xFF2A2C30),
    onSurfaceVariant = Color(0xFFB6B9BE),
    surfaceContainerLowest = Color(0xFF0F1012),
    surfaceContainerLow = Color(0xFF17181B),
    surfaceContainer = Color(0xFF1B1C1F),
    surfaceContainerHigh = Color(0xFF2A2C30),
    surfaceContainerHighest = Color(0xFF34363B),
    surfaceTint = Color(0xFF93ADF5),
    outline = Color(0xFF75787E),
    outlineVariant = Color(0xFF2F3135),
    error = Color(0xFFFF6B5C),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF3A1C19),
    onErrorContainer = Color(0xFFFF9A8F),
    inverseSurface = Color(0xFFE9EBEE),
    inverseOnSurface = Color(0xFF1B1C1F),
    inversePrimary = Color(0xFF2456E6),
)

/** The money colors Material has no slots for. Always paired with a sign and an icon (req NFR-06). */
@Immutable
data class ExpensifyColors(
    val income: Color,
    val incomeContainer: Color,
    val expense: Color,
    val expenseContainer: Color,
    val warn: Color,
    val warnContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val transfer: Color,
    /** How far user-picked colors (category and account icons) move toward white, so dark hues stay visible on dark surfaces. */
    val dataLift: Float,
)

val LightExpensifyColors = ExpensifyColors(
    income = Color(0xFF36B37E),
    incomeContainer = Color(0xFFEBF7F2),
    expense = Color(0xFFFF4530),
    expenseContainer = Color(0xFFFFECEA),
    warn = Color(0xFFFFAB00),
    warnContainer = Color(0xFFFFF7E6),
    info = Color(0xFF00B8D9),
    infoContainer = Color(0xFFE6F0FC),
    transfer = Color(0xFF646663),
    dataLift = 0f,
)

val DarkExpensifyColors = ExpensifyColors(
    income = Color(0xFF3FBF87),
    incomeContainer = Color(0xFF13291F),
    expense = Color(0xFFFF6B5C),
    expenseContainer = Color(0xFF3A1C19),
    warn = Color(0xFFF5B53D),
    warnContainer = Color(0xFF2E2513),
    info = Color(0xFF2EC4DF),
    infoContainer = Color(0xFF11282D),
    transfer = Color(0xFF9B9EA4),
    dataLift = 0.35f,
)

val LocalExpensifyColors = staticCompositionLocalOf { LightExpensifyColors }

private val FALLBACK_DATA_COLOR = Color(0xFF646663)

/** A stored `#RRGGBB`; anything unreadable falls back to neutral grey rather than crashing. */
fun parseHexColor(hex: String?): Color {
    if (hex == null || !Regex("#[0-9A-Fa-f]{6}").matches(hex)) return FALLBACK_DATA_COLOR
    return Color(0xFF000000 or hex.substring(1).toLong(16))
}

/** A category or account color as it should draw in the current theme. */
@Composable
fun dataColor(hex: String?): Color {
    val lift = LocalExpensifyColors.current.dataLift
    val color = parseHexColor(hex)
    return if (lift == 0f) color else lerp(color, Color.White, lift)
}

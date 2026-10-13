package com.example.expensify.designsystem.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.example.expensify.core.money.Money
import com.example.expensify.core.money.MoneyFormat
import com.example.expensify.core.money.Sign
import com.example.expensify.designsystem.theme.ExpensifyTheme

/** What an amount stands for, which picks its color. Never color alone: callers add a sign and an icon. */
enum class AmountKind { Neutral, Income, Expense, Transfer }

/**
 * An amount as every screen writes it: tabular digits where the font has
 * them, right-aligned, never wrapped, read aloud as "minus 450 rupees".
 */
@Composable
fun AmountText(
    money: Money,
    modifier: Modifier = Modifier,
    kind: AmountKind = AmountKind.Neutral,
    sign: Sign = Sign.Auto,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    masked: Boolean = false,
    textAlign: TextAlign = TextAlign.End,
) {
    val colors = ExpensifyTheme.colors
    val resolved = when {
        color != Color.Unspecified -> color
        kind == AmountKind.Income -> colors.income
        kind == AmountKind.Expense -> colors.expense
        kind == AmountKind.Transfer -> colors.transfer
        else -> Color.Unspecified
    }
    val text = if (masked) "••••" else MoneyFormat.full(money, sign)
    val spoken = if (masked) "hidden amount" else MoneyFormat.spoken(money)
    Text(
        text = text,
        modifier = modifier.semantics { contentDescription = spoken },
        color = resolved,
        style = style.copy(fontFeatureSettings = "tnum"),
        maxLines = 1,
        softWrap = false,
        textAlign = textAlign,
    )
}

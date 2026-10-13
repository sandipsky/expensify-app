package com.example.expensify.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.expensify.R
import com.example.expensify.core.money.Money
import com.example.expensify.core.money.Sign
import com.example.expensify.core.ui.UiText
import com.example.expensify.designsystem.icons.IconMap
import com.example.expensify.designsystem.theme.ExpensifyTheme
import com.example.expensify.designsystem.theme.dataColor

/** One transaction as lists draw it; the ViewModel resolves names, the row only draws. */
@Immutable
data class EntryRowUi(
    val id: String,
    /** The note, else the category, else the type. */
    val title: UiText,
    /** "Cash", or "Food · Cash" when the note is the title. */
    val subtitle: UiText,
    /** A stored lumen icon name; `IconMap` resolves it. */
    val iconName: String,
    /** `#RRGGBB`, or null for the neutral icon of transfers and deleted categories. */
    val colorHex: String?,
    val kind: AmountKind,
    /** Signed: negative for expenses, positive for income, the amount itself for transfers. */
    val amount: Money,
    val upcoming: Boolean,
)

/**
 * A 64 dp row: icon badge, title and detail line, the amount at the end with
 * its sign and color, and an "Upcoming" chip for future-dated entries.
 */
@Composable
fun EntryRow(
    row: EntryRowUi,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    masked: Boolean = false,
) {
    val neutral = ExpensifyTheme.colors.transfer
    val color = if (row.colorHex == null) neutral else dataColor(row.colorHex)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon = IconMap.of(row.iconName), color = color, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    row.title.asString(),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (row.upcoming) TagChip(stringResource(R.string.tx_upcoming), tone = TagTone.Info)
            }
            Text(
                row.subtitle.asString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        AmountText(
            money = row.amount,
            kind = row.kind,
            sign = if (row.kind == AmountKind.Transfer) Sign.Never else Sign.ExceptZero,
            style = MaterialTheme.typography.titleMedium,
            masked = masked,
        )
    }
}

/** A day's header in a grouped list: its label and its net, income green or expense red. */
@Composable
fun DayHeader(label: String, net: Money, modifier: Modifier = Modifier, masked: Boolean = false) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        AmountText(
            money = net,
            kind = when {
                net.isPositive -> AmountKind.Income
                net.isNegative -> AmountKind.Expense
                else -> AmountKind.Neutral
            },
            sign = Sign.ExceptZero,
            style = MaterialTheme.typography.labelMedium,
            masked = masked,
        )
    }
}

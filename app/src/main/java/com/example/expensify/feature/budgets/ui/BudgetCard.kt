package com.example.expensify.feature.budgets.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.expensify.R
import com.example.expensify.core.date.DateFormats
import com.example.expensify.core.money.MoneyFormat
import com.example.expensify.designsystem.components.AmountText
import com.example.expensify.designsystem.components.AppCard
import com.example.expensify.designsystem.components.IconBadge
import com.example.expensify.designsystem.components.Meter
import com.example.expensify.designsystem.components.Skeleton
import com.example.expensify.designsystem.components.TagChip
import com.example.expensify.designsystem.icons.AppIcons
import com.example.expensify.designsystem.icons.IconMap
import com.example.expensify.designsystem.theme.ExpensifyTheme
import com.example.expensify.designsystem.theme.dataColor
import com.example.expensify.feature.budgets.domain.BudgetPeriod
import com.example.expensify.feature.budgets.domain.BudgetProgress

/** The badge color a budget draws with: its category's, or the accent for a budget over several categories. */
@Composable
fun budgetIconColor(icon: BudgetLabels.Icon): Color =
    if (icon.colorHex == null) MaterialTheme.colorScheme.primary else dataColor(icon.colorHex)

/**
 * One budget on the Budgets page: name, what it counts, and its current
 * period, or just its limit while paused. Tapping the card opens the budget's page.
 */
@Composable
fun BudgetCard(
    card: BudgetCardUi,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val budget = card.budget
    var menuOpen by remember { mutableStateOf(false) }

    AppCard(modifier = modifier, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon = IconMap.of(card.icon.name), color = budgetIconColor(card.icon), size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(budget.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.budget_card_caption, stringResource(budget.period.labelRes()), card.scope.asString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(AppIcons.More, contentDescription = stringResource(R.string.budget_actions_for, budget.name))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    MenuItem(R.string.action_edit, AppIcons.Edit) { menuOpen = false; onEdit() }
                    if (budget.active) {
                        MenuItem(R.string.budget_pause, AppIcons.Pause) { menuOpen = false; onPause() }
                    } else {
                        MenuItem(R.string.budget_resume, AppIcons.Resume) { menuOpen = false; onResume() }
                    }
                    MenuItem(R.string.action_delete, AppIcons.Delete, danger = true) { menuOpen = false; onDelete() }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        when {
            !budget.active -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(budget.period.perPeriodRes(), MoneyFormat.full(budget.amount)),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                TagChip(stringResource(R.string.budget_chip_paused))
            }
            card.progress != null -> BudgetMeterView(progress = card.progress, period = budget.period, name = budget.name)
            else -> Skeleton(92.dp)
        }
    }
}

/**
 * A budget's current period at a glance (req BUD-02): spent of limit, a bar,
 * the state with its icon, what's left, and what's safe to spend each day.
 * With rollover it says what came in from the period before.
 */
@Composable
fun BudgetMeterView(progress: BudgetProgress, period: BudgetPeriod, name: String, modifier: Modifier = Modifier) {
    val result = progress.result
    val state = result.state.view()
    val previous = stringResource(period.previousRes())
    val over = result.remaining.isNegative

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            AmountText(
                money = result.spent,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Start,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.budget_spent_of, MoneyFormat.full(result.limit)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).alignByBaseline(),
            )
            Text(
                BudgetLabels.percent(result.percent).asString(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.alignByBaseline(),
            )
        }

        // A zero limit has nothing left, so its bar reads full.
        Meter(
            fraction = (result.percent ?: 100) / 100f,
            tone = state.meter,
            label = stringResource(R.string.budget_meter_label, name),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            TagChip(stringResource(state.labelRes), tone = state.tone, icon = state.icon)
            Spacer(Modifier.weight(1f))
            Text(
                if (over) {
                    stringResource(R.string.budget_remaining_over, MoneyFormat.full(-result.remaining))
                } else {
                    stringResource(R.string.budget_remaining_left, MoneyFormat.full(result.remaining))
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (over) ExpensifyTheme.colors.expense else Color.Unspecified,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(
                    R.string.budget_period_and_days,
                    DateFormats.period(result.range),
                    BudgetLabels.daysLeft(progress.daysLeft).asString(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    !progress.safePerDay.isPositive -> stringResource(R.string.budget_per_day_none)
                    progress.daysLeft == 1 -> stringResource(R.string.budget_per_day_today, MoneyFormat.full(progress.safePerDay))
                    else -> stringResource(R.string.budget_per_day, MoneyFormat.full(progress.safePerDay))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!result.carry.isZero) {
            Text(
                if (result.carry.isPositive) {
                    stringResource(R.string.budget_carry_in, MoneyFormat.full(result.carry), previous)
                } else {
                    stringResource(R.string.budget_carry_out, MoneyFormat.full(-result.carry), previous)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun MenuItem(labelRes: Int, icon: ImageVector, danger: Boolean = false, onClick: () -> Unit) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    DropdownMenuItem(
        text = { Text(stringResource(labelRes), color = if (danger) MaterialTheme.colorScheme.error else Color.Unspecified) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = tint) },
        onClick = onClick,
    )
}

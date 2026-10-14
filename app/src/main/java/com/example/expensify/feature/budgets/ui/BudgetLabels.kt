package com.example.expensify.feature.budgets.ui

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.expensify.R
import com.example.expensify.core.ui.UiText
import com.example.expensify.core.ui.plain
import com.example.expensify.core.ui.res
import com.example.expensify.designsystem.components.MeterTone
import com.example.expensify.designsystem.components.TagTone
import com.example.expensify.designsystem.icons.AppIcons
import com.example.expensify.feature.budgets.domain.Budget
import com.example.expensify.feature.budgets.domain.BudgetPeriod
import com.example.expensify.feature.budgets.domain.BudgetState
import com.example.expensify.feature.categories.domain.Category

// Port of ../expensify/src/features/budgets/budget.labels.ts: how budgets read on screen.

@StringRes
fun BudgetPeriod.labelRes(): Int = when (this) {
    BudgetPeriod.Weekly -> R.string.budget_period_weekly
    BudgetPeriod.Monthly -> R.string.budget_period_monthly
    BudgetPeriod.Yearly -> R.string.budget_period_yearly
}

/** "%1$s a week", for "Rs 500.00 a week". */
@StringRes
fun BudgetPeriod.perPeriodRes(): Int = when (this) {
    BudgetPeriod.Weekly -> R.string.budget_per_week
    BudgetPeriod.Monthly -> R.string.budget_per_month
    BudgetPeriod.Yearly -> R.string.budget_per_year
}

/** How the previous period is named where rollover mentions it. */
@StringRes
fun BudgetPeriod.previousRes(): Int = when (this) {
    BudgetPeriod.Weekly -> R.string.budget_previous_week
    BudgetPeriod.Monthly -> R.string.budget_previous_month
    BudgetPeriod.Yearly -> R.string.budget_previous_year
}

/** How a state reads: a label and an icon alongside the color, never color alone (req NFR-06). */
data class StateView(@StringRes val labelRes: Int, val icon: ImageVector, val tone: TagTone, val meter: MeterTone)

fun BudgetState.view(): StateView = when (this) {
    BudgetState.OnTrack -> StateView(R.string.budget_state_on_track, AppIcons.OnTrack, TagTone.Success, MeterTone.Accent)
    BudgetState.Warning -> StateView(R.string.budget_state_warning, AppIcons.Warning, TagTone.Warn, MeterTone.Warn)
    BudgetState.Over -> StateView(R.string.budget_state_over, AppIcons.Over, TagTone.Error, MeterTone.Error)
}

object BudgetLabels {

    /** What a budget over every expense, or over several categories, draws, in the accent color. */
    const val GENERIC_ICON = "donut_large"

    /** The badge a budget wears: a lumen icon name and a `#RRGGBB`, or null for the accent color. */
    data class Icon(val name: String, val colorHex: String?)

    /** A single-category budget wears its category's icon; others a generic one. */
    fun iconOf(budget: Budget, categoriesById: Map<String, Category>): Icon {
        val only = if (budget.categoryIds.size == 1) categoriesById[budget.categoryIds[0]] else null
        return if (only != null) Icon(only.icon, only.color) else Icon(GENERIC_ICON, null)
    }

    /** What the budget counts: "All expenses", or its categories as "Food and dining, Transport" (req CAT-05 for deleted ones). */
    fun scopeOf(budget: Budget, categoriesById: Map<String, Category>): UiText {
        if (budget.categoryIds.isEmpty()) return res(R.string.budget_scope_all)
        val names = budget.categoryIds.mapNotNull { categoriesById[it]?.name }
        return if (names.isEmpty()) res(R.string.budget_scope_deleted) else plain(names.joinToString(", "))
    }

    /** "5 days left", "Last day", "Ended". */
    fun daysLeft(days: Int): UiText = when {
        days <= 0 -> res(R.string.budget_ended)
        days == 1 -> res(R.string.budget_last_day)
        else -> res(R.string.budget_days_left, days)
    }

    /** "86%", or "—" for a zero limit. */
    fun percent(percent: Int?): UiText =
        if (percent == null) res(R.string.budget_percent_none) else res(R.string.budget_percent, percent)
}

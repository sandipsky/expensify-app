package com.example.expensify.feature.categories.ui

import androidx.annotation.StringRes
import com.example.expensify.R
import com.example.expensify.feature.categories.domain.CategoryType

/** Port of ../expensify/src/features/categories/category.labels.ts. */
@StringRes
fun CategoryType.labelRes(): Int = when (this) {
    CategoryType.Expense -> R.string.category_type_expense
    CategoryType.Income -> R.string.category_type_income
}

/** Placeholder names, so the field hints at what goes in each list. */
@StringRes
fun CategoryType.placeholderRes(): Int = when (this) {
    CategoryType.Expense -> R.string.category_form_name_placeholder_expense
    CategoryType.Income -> R.string.category_form_name_placeholder_income
}

@StringRes
fun CategoryType.emptyTitleRes(): Int = when (this) {
    CategoryType.Expense -> R.string.categories_empty_expense_title
    CategoryType.Income -> R.string.categories_empty_income_title
}

@StringRes
fun CategoryType.nameTakenRes(): Int = when (this) {
    CategoryType.Expense -> R.string.category_form_error_name_taken_expense
    CategoryType.Income -> R.string.category_form_error_name_taken_income
}

/** How an icon tile is read aloud: "bar_chart" reads "bar chart". */
fun iconLabel(icon: String): String = icon.replace('_', ' ')

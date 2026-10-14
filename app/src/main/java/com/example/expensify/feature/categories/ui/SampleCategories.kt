package com.example.expensify.feature.categories.ui

import com.example.expensify.feature.categories.domain.Category
import com.example.expensify.feature.categories.domain.CategoryRules
import com.example.expensify.feature.categories.domain.CategoryType
import java.time.Instant

/** Realistic fixtures for previews: the defaults, with one archived. */
object SampleCategories {
    private val at = Instant.parse("2026-10-01T07:10:33Z")

    val all: List<Category> = CategoryRules.DEFAULTS.mapIndexed { index, d ->
        Category(
            id = "c$index",
            name = d.name,
            type = d.type,
            icon = d.icon,
            color = d.color,
            archived = d.name == "Insurance",
            sortOrder = index.toLong(),
            createdAt = at,
            updatedAt = at,
        )
    }

    val food: Category = all.first { it.name == "Food and dining" }
    val transport: Category = all.first { it.name == "Transport" }
    val expense: CategoryRules.Lists = CategoryRules.lists(all, CategoryType.Expense)
    val income: CategoryRules.Lists = CategoryRules.lists(all, CategoryType.Income)
}

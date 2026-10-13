package com.example.expensify.feature.categories.domain

import java.time.Instant

enum class CategoryType(val stored: String) {
    Expense("expense"),
    Income("income"),
    ;

    companion object {
        fun fromStored(value: String?): CategoryType = entries.firstOrNull { it.stored == value } ?: Expense
    }
}

/** users/{uid}/categories/{id} (req §8). */
data class Category(
    val id: String,
    /** 1–30 characters, unique within its type ignoring case. */
    val name: String,
    /** Fixed once created, since entries of that type use it. */
    val type: CategoryType,
    /** A lumen-ui icon name; `IconMap` draws it. */
    val icon: String,
    /** `#RRGGBB`. */
    val color: String,
    /** Hidden from pickers; existing entries keep it. */
    val archived: Boolean,
    val sortOrder: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** By `sortOrder`, then name (req CAT-07). */
val categoryOrder: Comparator<Category> = compareBy<Category> { it.sortOrder }.thenBy { it.name.lowercase() }

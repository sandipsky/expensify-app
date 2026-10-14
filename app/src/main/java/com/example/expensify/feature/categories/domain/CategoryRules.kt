package com.example.expensify.feature.categories.domain

import kotlin.random.Random

/** One of the categories a new account starts with (req Appendix A). */
data class DefaultCategory(val type: CategoryType, val name: String, val icon: String, val color: String)

/** Port of ../expensify/src/features/categories/category.domain.ts: the rules the categories screens and pickers share. */
object CategoryRules {

    /** Longest category name (req BR-15). */
    const val MAX_NAME_LENGTH = 30

    /**
     * The colors a new category can get (req CAT-02). Each keeps at least 3:1
     * contrast against its own 12 % tint, the icon badge's background.
     */
    val COLORS: List<String> = listOf(
        "#DC2626", // red
        "#EA580C", // orange
        "#B45309", // amber
        "#15803D", // green
        "#0D9488", // teal
        "#0284C7", // sky
        "#2456E6", // blue
        "#4F46E5", // indigo
        "#9333EA", // purple
        "#DB2777", // pink
        "#8D6E63", // brown
        "#64748B", // slate
    )

    /** The 48 icons the picker offers: the generic default first, then roughly by theme. Stored as lumen-ui names. */
    val ICONS: List<String> = listOf(
        "category", "cart", "bag", "checkroom", "home", "building", "bolt", "wifi",
        "call", "car", "fuel", "train", "truck", "flight", "location", "hospital",
        "fitness", "heart", "family", "users", "education", "notebook", "music", "controller",
        "image", "star", "calendar", "event_repeat", "refresh", "receipt", "file", "shield",
        "lock", "work", "factory", "money", "moneys", "card", "bank", "savings",
        "wallet", "bar_chart", "pie_chart", "hand", "configuration", "box", "undo", "add",
    )

    /** A new category's icon until the user picks one. */
    const val DEFAULT_ICON = "category"

    /** The categories a new account starts with, and what "Add default categories" adds back, in each type's list order. */
    val DEFAULTS: List<DefaultCategory> = listOf(
        DefaultCategory(CategoryType.Expense, "Food and dining", "category", "#EA580C"),
        DefaultCategory(CategoryType.Expense, "Groceries", "cart", "#15803D"),
        DefaultCategory(CategoryType.Expense, "Transport", "train", "#2456E6"),
        DefaultCategory(CategoryType.Expense, "Housing and rent", "home", "#8D6E63"),
        DefaultCategory(CategoryType.Expense, "Utilities", "bolt", "#B45309"),
        DefaultCategory(CategoryType.Expense, "Phone and internet", "wifi", "#0284C7"),
        DefaultCategory(CategoryType.Expense, "Shopping", "bag", "#DB2777"),
        DefaultCategory(CategoryType.Expense, "Health", "hospital", "#DC2626"),
        DefaultCategory(CategoryType.Expense, "Education", "education", "#4F46E5"),
        DefaultCategory(CategoryType.Expense, "Entertainment", "controller", "#9333EA"),
        DefaultCategory(CategoryType.Expense, "Subscriptions", "refresh", "#0D9488"),
        DefaultCategory(CategoryType.Expense, "Travel", "flight", "#0284C7"),
        DefaultCategory(CategoryType.Expense, "Personal care", "heart", "#DB2777"),
        DefaultCategory(CategoryType.Expense, "Gifts and donations", "hand", "#DC2626"),
        DefaultCategory(CategoryType.Expense, "Family and kids", "family", "#B45309"),
        DefaultCategory(CategoryType.Expense, "Insurance", "shield", "#0D9488"),
        DefaultCategory(CategoryType.Expense, "Fees and charges", "receipt", "#64748B"),
        DefaultCategory(CategoryType.Income, "Salary", "money", "#15803D"),
        DefaultCategory(CategoryType.Income, "Business and freelance", "work", "#2456E6"),
        DefaultCategory(CategoryType.Income, "Bonus", "star", "#B45309"),
        DefaultCategory(CategoryType.Income, "Interest and dividends", "savings", "#0D9488"),
        DefaultCategory(CategoryType.Income, "Rental income", "building", "#8D6E63"),
        DefaultCategory(CategoryType.Income, "Gifts received", "heart", "#DB2777"),
        DefaultCategory(CategoryType.Income, "Refunds", "undo", "#0284C7"),
        DefaultCategory(CategoryType.Income, "Other income", "add", "#4F46E5"),
    )

    /** The next place in a type's list: after its last category (req CAT-07). */
    fun nextSortOrder(categories: List<Category>, type: CategoryType): Long =
        nextPlacedOrder(categories.map { it.type to it.sortOrder }, type)

    private fun nextPlacedOrder(placed: List<Pair<CategoryType, Long>>, type: CategoryType): Long =
        (placed.filter { it.first == type }.maxOfOrNull { it.second } ?: -1L) + 1

    /** How names compare for uniqueness: trimmed and ignoring case. */
    fun nameKey(name: String): String = name.trim().lowercase()

    /**
     * Whether another category of the same type already has this name, ignoring
     * case (req CAT-02). Archived categories count, so restoring one can never
     * make a duplicate. Pass the category's own id when renaming it.
     */
    fun isNameTaken(categories: List<Category>, name: String, type: CategoryType, exceptId: String? = null): Boolean {
        val key = nameKey(name)
        return categories.any { it.id != exceptId && it.type == type && nameKey(it.name) == key }
    }

    /** How the Categories page lists one type's categories. */
    data class Lists(val active: List<Category>, val archived: List<Category>)

    fun lists(categories: List<Category>, type: CategoryType): Lists {
        val ofType = categories.filter { it.type == type }.sortedWith(categoryOrder)
        return Lists(active = ofType.filter { !it.archived }, archived = ofType.filter { it.archived })
    }

    /**
     * A color for a new category, picked at random. Colors the type's categories
     * don't use yet come first, so new ones look different from their neighbours;
     * once every color is taken, any palette color will do.
     */
    fun randomColor(categories: List<Category>, type: CategoryType, random: Random = Random.Default): String {
        val used = categories.filter { it.type == type }.map { it.color.uppercase() }.toSet()
        val unused = COLORS.filter { it !in used }
        val pool = unused.ifEmpty { COLORS }
        return pool[random.nextInt(pool.size)]
    }

    /** A default the user doesn't have yet, with the sort order it gets. */
    data class Placed(val entry: DefaultCategory, val sortOrder: Long)

    /**
     * The defaults missing from `existing`, matched by type and name ignoring
     * case, each at the end of its type's list (req CAT-06). Empty when the
     * user already has every one.
     */
    fun missingDefaults(existing: List<Category>): List<Placed> {
        val placed = existing.map { it.type to it.sortOrder }.toMutableList()
        val result = mutableListOf<Placed>()
        for (entry in DEFAULTS) {
            if (isNameTaken(existing, entry.name, entry.type)) continue
            val sortOrder = nextPlacedOrder(placed, entry.type)
            placed += entry.type to sortOrder
            result += Placed(entry, sortOrder)
        }
        return result
    }
}

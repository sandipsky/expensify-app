package com.example.expensify.feature.categories.domain

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlin.random.Random
import org.junit.Test

/** Req CAT-02, CAT-06, CAT-07: unique names per type, defaults, list order and colors. */
class CategoryRulesTest {

    private fun category(
        id: String,
        name: String,
        type: CategoryType = CategoryType.Expense,
        sortOrder: Long = 0,
        archived: Boolean = false,
        color: String = "#DC2626",
    ) = Category(
        id = id,
        name = name,
        type = type,
        icon = "category",
        color = color,
        archived = archived,
        sortOrder = sortOrder,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private val food = category("c1", "Food and dining", sortOrder = 0)
    private val transport = category("c2", "Transport", sortOrder = 1, color = "#2456E6")
    private val oldRent = category("c3", "Rent", sortOrder = 2, archived = true, color = "#8D6E63")
    private val salary = category("c4", "Salary", CategoryType.Income, sortOrder = 0, color = "#15803D")
    private val all = listOf(salary, oldRent, transport, food)

    @Test
    fun `a new category goes to the end of its type`() {
        assertThat(CategoryRules.nextSortOrder(all, CategoryType.Expense)).isEqualTo(3)
        assertThat(CategoryRules.nextSortOrder(all, CategoryType.Income)).isEqualTo(1)
        assertThat(CategoryRules.nextSortOrder(emptyList(), CategoryType.Expense)).isEqualTo(0)
    }

    @Test
    fun `names are unique within a type ignoring case, archived ones included`() {
        assertThat(CategoryRules.isNameTaken(all, "  food AND Dining ", CategoryType.Expense)).isTrue()
        assertThat(CategoryRules.isNameTaken(all, "rent", CategoryType.Expense)).isTrue()
        assertThat(CategoryRules.isNameTaken(all, "Salary", CategoryType.Expense)).isFalse()
        assertThat(CategoryRules.isNameTaken(all, "Transport", CategoryType.Expense, exceptId = "c2")).isFalse()
        assertThat(CategoryRules.isNameTaken(all, "Transport", CategoryType.Expense, exceptId = "c1")).isTrue()
    }

    @Test
    fun `lists sort by sortOrder then name and split archived ones`() {
        val lists = CategoryRules.lists(all, CategoryType.Expense)
        assertThat(lists.active.map { it.id }).containsExactly("c1", "c2").inOrder()
        assertThat(lists.archived.map { it.id }).containsExactly("c3")
        val tie = listOf(category("b", "Beta", sortOrder = 5), category("a", "alpha", sortOrder = 5))
        assertThat(CategoryRules.lists(tie, CategoryType.Expense).active.map { it.id }).containsExactly("a", "b").inOrder()
    }

    @Test
    fun `a random color prefers ones the type does not use yet`() {
        val expenseColors = all.filter { it.type == CategoryType.Expense }.map { it.color }
        repeat(50) {
            assertThat(CategoryRules.randomColor(all, CategoryType.Expense)).isNotIn(expenseColors)
            assertThat(CategoryRules.randomColor(all, CategoryType.Expense)).isIn(CategoryRules.COLORS)
        }
        // Every color taken: any palette color will do.
        val full = CategoryRules.COLORS.mapIndexed { i, c -> category("x$i", "n$i", sortOrder = i.toLong(), color = c.lowercase()) }
        assertThat(CategoryRules.randomColor(full, CategoryType.Expense, Random(1))).isIn(CategoryRules.COLORS)
    }

    @Test
    fun `missing defaults are matched by type and name and placed at the end of their type`() {
        val placed = CategoryRules.missingDefaults(all)
        val names = placed.map { it.entry.name }
        assertThat(names).doesNotContain("Food and dining")
        assertThat(names).doesNotContain("Salary")
        assertThat(names).doesNotContain("Transport")
        assertThat(names).contains("Groceries")
        assertThat(placed.size).isEqualTo(CategoryRules.DEFAULTS.size - 3)
        // Expense defaults continue after sort order 2, income ones after 0.
        val groceries = placed.first { it.entry.name == "Groceries" }
        assertThat(groceries.sortOrder).isEqualTo(3)
        val bonus = placed.first { it.entry.name == "Bonus" }
        assertThat(bonus.sortOrder).isEqualTo(2)
        val business = placed.first { it.entry.name == "Business and freelance" }
        assertThat(business.sortOrder).isEqualTo(1)
    }

    @Test
    fun `nothing to add when every default exists`() {
        val complete = CategoryRules.DEFAULTS.mapIndexed { i, d -> category("d$i", d.name.uppercase(), d.type, i.toLong()) }
        assertThat(CategoryRules.missingDefaults(complete)).isEmpty()
        assertThat(CategoryRules.missingDefaults(emptyList())).hasSize(25)
    }

    @Test
    fun `the palette and icon set match the web`() {
        assertThat(CategoryRules.COLORS).hasSize(12)
        assertThat(CategoryRules.ICONS).hasSize(48)
        assertThat(CategoryRules.ICONS.first()).isEqualTo(CategoryRules.DEFAULT_ICON)
        assertThat(CategoryRules.DEFAULTS.count { it.type == CategoryType.Expense }).isEqualTo(17)
        assertThat(CategoryRules.DEFAULTS.count { it.type == CategoryType.Income }).isEqualTo(8)
    }
}

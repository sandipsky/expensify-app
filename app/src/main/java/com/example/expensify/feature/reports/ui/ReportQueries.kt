package com.example.expensify.feature.reports.ui

import androidx.lifecycle.SavedStateHandle
import com.example.expensify.core.date.DateRange
import com.example.expensify.core.date.Periods
import com.example.expensify.core.date.Today
import com.example.expensify.core.date.toLocalDateOrNull
import com.example.expensify.core.date.ymd
import com.example.expensify.core.error.ErrorMessages
import com.example.expensify.core.ui.UiText
import com.example.expensify.core.ui.plain
import com.example.expensify.feature.accounts.data.AccountRepository
import com.example.expensify.feature.accounts.domain.Account
import com.example.expensify.feature.categories.data.CategoryRepository
import com.example.expensify.feature.categories.domain.Category
import com.example.expensify.feature.reports.domain.PeriodState
import com.example.expensify.feature.reports.domain.ReportPreset
import com.example.expensify.feature.reports.domain.ReportRules
import com.example.expensify.feature.transactions.domain.Transaction
import com.example.expensify.feature.transactions.domain.TxType
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * What every report reads besides its own figures (port of useReportBase.ts):
 * today, and the accounts and categories it names things from.
 */
data class ReportBase(val today: LocalDate, val accounts: List<Account>, val categories: List<Category>) {
    val accountsById: Map<String, Account> = accounts.associateBy { it.id }
    val categoriesById: Map<String, Category> = categories.associateBy { it.id }
}

internal fun reportBase(accounts: AccountRepository, categories: CategoryRepository, today: Today): Flow<ReportBase> =
    combine(accounts.accounts(), categories.categories(), today.date) { a, c, day -> ReportBase(day, a, c) }

/** A transactions query's answer: still loading, loaded, or refused with the sentence for it. */
sealed interface Loaded {
    data object Loading : Loaded
    data class Ok(val txs: List<Transaction>) : Loaded
    data class Failed(val message: UiText) : Loaded
}

internal fun Flow<List<Transaction>>.loaded(): Flow<Loaded> =
    map<List<Transaction>, Loaded> { Loaded.Ok(it) }
        .onStart { emit(Loaded.Loading) }
        .catch { emit(Loaded.Failed(plain(ErrorMessages.of(it)))) }

/** The transactions behind a figure, as a report hands them to the list (req RPT-02). */
data class DrillDown(
    val range: DateRange,
    val type: TxType? = null,
    val categoryIds: List<String> = emptyList(),
    val accountIds: List<String> = emptyList(),
)

/** Account and category filters travel as id lists, "id1,id2", in route arguments. */
object IdsParam {
    /** What a document id can be in a link: letters and digits, as Firestore's auto ids are. */
    private val ID = Regex("[A-Za-z0-9]{1,64}")

    /** The ids an id-list argument holds; anything that can't be an id is dropped. */
    fun parse(param: String?): List<String> = param.orEmpty().split(',').filter { ID.matches(it) }

    /** Ids as an argument: "id1,id2", or null for none. */
    fun of(ids: List<String>): String? = ids.takeIf { it.isNotEmpty() }?.joinToString(",")
}

/** The period a report shows, and whether the route set it (so Reset is offered). */
data class PeriodArgsState(val state: PeriodState, val isSet: Boolean)

/**
 * The period part of a report's route (`period`, `from`, `to`), read from
 * and written back to the SavedStateHandle so Back, process death and a link
 * into the report all restore it, as the web keeps it in the URL (req RPT-02).
 */
internal class PeriodArgs(private val handle: SavedStateHandle, private val fallback: ReportPreset) {
    private val preset = handle.getStateFlow<String?>(KEY_PERIOD, null)
    private val from = handle.getStateFlow<String?>(KEY_FROM, null)
    private val to = handle.getStateFlow<String?>(KEY_TO, null)

    fun observe(today: Flow<LocalDate>): Flow<PeriodArgsState> = combine(preset, from, to, today) { p, f, t, day ->
        PeriodArgsState(
            state = ReportRules.periodState(ReportPreset.fromStored(p), f?.toLocalDateOrNull(), t?.toLocalDateOrNull(), fallback, day),
            isSet = p != null || f != null || t != null,
        )
    }

    /** Picking "Custom" keeps the range the report showed, so the dates have a sensible start. */
    fun setPreset(value: ReportPreset, shown: DateRange) {
        if (value == ReportPreset.Custom) {
            handle[KEY_FROM] = shown.start.ymd()
            handle[KEY_TO] = shown.end.ymd()
        } else {
            handle[KEY_FROM] = null
            handle[KEY_TO] = null
        }
        handle[KEY_PERIOD] = value.stored
    }

    /** A custom range; reversed dates are swapped. */
    fun setCustom(start: LocalDate, end: LocalDate) {
        val range = Periods.ordered(start, end)
        handle[KEY_FROM] = range.start.ymd()
        handle[KEY_TO] = range.end.ymd()
        handle[KEY_PERIOD] = ReportPreset.Custom.stored
    }

    fun reset() {
        handle[KEY_PERIOD] = null
        handle[KEY_FROM] = null
        handle[KEY_TO] = null
    }

    private companion object {
        const val KEY_PERIOD = "period"
        const val KEY_FROM = "from"
        const val KEY_TO = "to"
    }
}

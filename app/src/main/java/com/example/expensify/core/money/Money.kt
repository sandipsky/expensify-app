package com.example.expensify.core.money

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Exact money in paisa. Firestore stores amounts as Doubles with at most two
 * decimals (req BR-01); on the device they are summed here as Longs, so
 * 0.1 + 0.2 is 0.3 and a balance never drifts by a hair of a paisa.
 */
@JvmInline
value class Money(val paisa: Long) : Comparable<Money> {

    val isZero: Boolean get() = paisa == 0L
    val isNegative: Boolean get() = paisa < 0
    val isPositive: Boolean get() = paisa > 0

    operator fun plus(other: Money): Money = Money(paisa + other.paisa)
    operator fun minus(other: Money): Money = Money(paisa - other.paisa)
    operator fun unaryMinus(): Money = Money(-paisa)
    operator fun times(count: Int): Money = Money(paisa * count)

    fun abs(): Money = Money(abs(paisa))

    /** The Double Firestore stores: rupees with two decimals. */
    fun toStored(): Double = paisa / 100.0

    override fun compareTo(other: Money): Int = paisa.compareTo(other.paisa)

    override fun toString(): String = "Money(${toStored()})"

    companion object {
        val ZERO = Money(0)

        /** A stored Double as exact paisa; rounding absorbs float noise such as 28.999999. */
        fun fromStored(value: Double): Money = Money((value * 100).roundToLong())

        /** `rupees(-450, 50)` is minus 450.50: the sign covers the paisa too. */
        fun rupees(rupees: Long, paisa: Long = 0): Money {
            val magnitude = abs(rupees) * 100 + paisa
            return Money(if (rupees < 0) -magnitude else magnitude)
        }

        /**
         * What a user typed in an amount field: digits with an optional sign,
         * commas, spaces and up to two decimals (more are rounded half up).
         * Null for anything that isn't an amount yet, such as "" or "-".
         */
        fun parse(text: String): Money? {
            val clean = text.replace(",", "").replace(" ", "").trim()
            if (clean.isEmpty() || clean == "-" || clean == "." || clean == "-.") return null
            val value = clean.toBigDecimalOrNull() ?: return null
            return Money(value.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact())
        }
    }
}

fun Iterable<Money>.sum(): Money = fold(Money.ZERO) { acc, m -> acc + m }

/** A plain rupee figure as a BigDecimal, for percentages and divisions. */
fun Money.toBigDecimal(): BigDecimal = BigDecimal.valueOf(paisa, 2)

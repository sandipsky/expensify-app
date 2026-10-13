package com.example.expensify.core.money

import kotlin.math.roundToLong

/** Whether a sign is written: like Intl.NumberFormat's `signDisplay`. */
enum class Sign { Auto, Always, ExceptZero, Never }

/**
 * Amounts are always written the Nepali way, whatever the device language:
 * lakh and crore grouping with Latin digits, "Rs 12,34,567.50" (req BR-03).
 * Pure Kotlin on purpose, so it runs the same in unit tests and on every OEM.
 */
object MoneyFormat {
    const val SYMBOL = "Rs"

    private val UNITS = listOf(10_000_000.0 to "Cr", 100_000.0 to "L", 1_000.0 to "K")

    /** "Rs 12,34,567.50", "-Rs 450.00", or with `Sign.ExceptZero` "+Rs 5,000.00". */
    fun full(money: Money, sign: Sign = Sign.Auto): String {
        val abs = money.abs()
        val rupees = abs.paisa / 100
        val paisa = abs.paisa % 100
        return prefix(money, sign) + "$SYMBOL ${groupIndian(rupees)}.${paisa.toString().padStart(2, '0')}"
    }

    /**
     * Short amounts for chart axes and captions: "Rs 1.2L", "Rs 45K",
     * "Rs 1.2Cr"; below a thousand the rupees without decimals.
     */
    fun compact(money: Money, sign: Sign = Sign.Auto): String {
        val rupees = money.abs().paisa / 100.0
        val unit = UNITS.firstOrNull { (threshold, _) -> rupees >= threshold }
        val text = if (unit == null) {
            groupIndian(rupees.roundToLong())
        } else {
            oneDecimal(rupees / unit.first) + unit.second
        }
        return prefix(money, sign) + "$SYMBOL $text"
    }

    /** How a screen reader says it: "minus 450 rupees 50 paisa". */
    fun spoken(money: Money): String {
        val abs = money.abs()
        val rupees = abs.paisa / 100
        val paisa = abs.paisa % 100
        val sign = if (money.isNegative) "minus " else ""
        val tail = if (paisa == 0L) "" else " $paisa paisa"
        return "$sign$rupees rupees$tail"
    }

    /** Lakh and crore grouping: 1234567 reads "12,34,567". */
    fun groupIndian(n: Long): String {
        val digits = n.toString()
        if (digits.length <= 3) return digits
        val last = digits.takeLast(3)
        val rest = digits.dropLast(3)
        val groups = rest.reversed().chunked(2).map { it.reversed() }.reversed()
        return groups.joinToString(",") + "," + last
    }

    private fun oneDecimal(value: Double): String {
        val tenths = (value * 10).roundToLong()
        return if (tenths % 10 == 0L) (tenths / 10).toString() else "${tenths / 10}.${tenths % 10}"
    }

    private fun prefix(money: Money, sign: Sign): String = when {
        money.isNegative -> if (sign == Sign.Never) "" else "-"
        sign == Sign.Always -> "+"
        sign == Sign.ExceptZero && !money.isZero -> "+"
        else -> ""
    }
}

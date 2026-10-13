package com.example.expensify.core.money

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The examples of req BR-03 and ../expensify/src/utils/amount.ts. */
class MoneyFormatTest {

    @Test
    fun `full uses lakh and crore grouping`() {
        assertThat(MoneyFormat.full(Money.rupees(1234567, 50))).isEqualTo("Rs 12,34,567.50")
        assertThat(MoneyFormat.full(Money.rupees(100000))).isEqualTo("Rs 1,00,000.00")
        assertThat(MoneyFormat.full(Money.rupees(999))).isEqualTo("Rs 999.00")
        assertThat(MoneyFormat.full(Money.rupees(1000))).isEqualTo("Rs 1,000.00")
        assertThat(MoneyFormat.full(Money.ZERO)).isEqualTo("Rs 0.00")
    }

    @Test
    fun `signs follow signDisplay`() {
        val plus = Money.rupees(5000)
        val minus = Money.rupees(-450)
        assertThat(MoneyFormat.full(minus)).isEqualTo("-Rs 450.00")
        assertThat(MoneyFormat.full(plus)).isEqualTo("Rs 5,000.00")
        assertThat(MoneyFormat.full(plus, Sign.ExceptZero)).isEqualTo("+Rs 5,000.00")
        assertThat(MoneyFormat.full(Money.ZERO, Sign.ExceptZero)).isEqualTo("Rs 0.00")
        assertThat(MoneyFormat.full(Money.ZERO, Sign.Always)).isEqualTo("+Rs 0.00")
        assertThat(MoneyFormat.full(minus, Sign.Never)).isEqualTo("Rs 450.00")
    }

    @Test
    fun `compact reads in K, L and Cr`() {
        assertThat(MoneyFormat.compact(Money.rupees(120000))).isEqualTo("Rs 1.2L")
        assertThat(MoneyFormat.compact(Money.rupees(45000))).isEqualTo("Rs 45K")
        assertThat(MoneyFormat.compact(Money.rupees(12000000))).isEqualTo("Rs 1.2Cr")
        assertThat(MoneyFormat.compact(Money.rupees(450))).isEqualTo("Rs 450")
        assertThat(MoneyFormat.compact(Money.rupees(1500))).isEqualTo("Rs 1.5K")
        assertThat(MoneyFormat.compact(Money.rupees(-250000))).isEqualTo("-Rs 2.5L")
    }

    @Test
    fun `spoken for screen readers`() {
        assertThat(MoneyFormat.spoken(Money.rupees(-450, 50))).isEqualTo("minus 450 rupees 50 paisa")
        assertThat(MoneyFormat.spoken(Money.rupees(12))).isEqualTo("12 rupees")
    }
}

package com.example.expensify.core.money

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MoneyTest {

    @Test
    fun `fromStored absorbs float noise`() {
        assertThat(Money.fromStored(0.29).paisa).isEqualTo(29)
        assertThat(Money.fromStored(1234.5).paisa).isEqualTo(123450)
        assertThat(Money.fromStored(-42000.0).paisa).isEqualTo(-4200000)
    }

    @Test
    fun `sums are exact where doubles are not`() {
        val sum = listOf(Money.fromStored(0.1), Money.fromStored(0.2)).sum()
        assertThat(sum).isEqualTo(Money.fromStored(0.3))
        assertThat(sum.toStored()).isEqualTo(0.3)
    }

    @Test
    fun `parse accepts what people type`() {
        assertThat(Money.parse("1234.5")).isEqualTo(Money.rupees(1234, 50))
        assertThat(Money.parse("12,34,567.50")).isEqualTo(Money.rupees(1234567, 50))
        assertThat(Money.parse("-20")).isEqualTo(Money.rupees(-20))
        assertThat(Money.parse(" 450 ")).isEqualTo(Money.rupees(450))
        assertThat(Money.parse("0.005")).isEqualTo(Money(1))
    }

    @Test
    fun `parse is null for anything that is not an amount yet`() {
        assertThat(Money.parse("")).isNull()
        assertThat(Money.parse("-")).isNull()
        assertThat(Money.parse(".")).isNull()
        assertThat(Money.parse("abc")).isNull()
    }

    @Test
    fun `arithmetic and comparison`() {
        val a = Money.rupees(100)
        val b = Money.rupees(30, 25)
        assertThat(a - b).isEqualTo(Money.rupees(69, 75))
        assertThat(-b).isEqualTo(Money(-3025))
        assertThat(a > b).isTrue()
        assertThat(Money.ZERO.isZero).isTrue()
    }
}

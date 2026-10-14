/*
 * Copyright 2026 The promokit Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.nephets.promokit.core;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void stringConstructorScalesToTwoPlaces() {
        assertThat(Money.of("19.9", "CNY").amount()).isEqualByComparingTo("19.90");
        assertThat(Money.of("19.994", "CNY").amount()).isEqualByComparingTo("19.99");
        assertThat(Money.of(new BigDecimal("10.005"), "CNY").amount())
                .isEqualByComparingTo("10.01");
        assertThat(Money.of(20L, "CNY").amount()).isEqualByComparingTo("20.00");
    }

    @Test
    void zeroKeepsCurrency() {
        Money zero = Money.zero("USD");
        assertThat(zero.isZero()).isTrue();
        assertThat(zero.currency()).isEqualTo("USD");
    }

    @Test
    void addAndSubtract() {
        Money a = Money.of("10.50", "CNY");
        Money b = Money.of("3.25", "CNY");
        assertThat(a.add(b)).isEqualTo(Money.of("13.75", "CNY"));
        assertThat(a.subtract(b)).isEqualTo(Money.of("7.25", "CNY"));
    }

    @Test
    void negateAndMultiply() {
        Money a = Money.of("8.00", "CNY");
        assertThat(a.negate()).isEqualTo(Money.of("-8.00", "CNY"));
        assertThat(a.multiply(new BigDecimal("0.75"))).isEqualTo(Money.of("6.00", "CNY"));
        assertThat(a.multiply(3)).isEqualTo(Money.of("24.00", "CNY"));
    }

    @Test
    void minAndMaxPickByAmount() {
        Money small = Money.of("5.00", "CNY");
        Money big = Money.of("9.00", "CNY");
        assertThat(small.min(big)).isSameAs(small);
        assertThat(small.max(big)).isSameAs(big);
    }

    @Test
    void signChecks() {
        assertThat(Money.of("-1.00", "CNY").isNegative()).isTrue();
        assertThat(Money.of("0.00", "CNY").isZero()).isTrue();
        assertThat(Money.of("0.01", "CNY").isPositive()).isTrue();
        assertThat(Money.of("12.30", "CNY")).hasToString("12.30 CNY");
    }

    @Test
    void compareToOrdersByAmount() {
        assertThat(Money.of("1.00", "CNY")).isLessThan(Money.of("2.00", "CNY"));
        assertThat(Money.of("2.00", "CNY")).isEqualByComparingTo(Money.of("2.00", "CNY"));
    }

    @Test
    void equalsIncludesCurrency() {
        assertThat(Money.of("1.00", "CNY")).isEqualTo(Money.of("1.00", "CNY"));
        assertThat(Money.of("1.00", "CNY")).isNotEqualTo(Money.of("1.00", "USD"));
        assertThat(Money.of("1.00", "CNY").hashCode())
                .isEqualTo(Money.of("1.00", "CNY").hashCode());
        assertThat(Money.of("1.00", "CNY")).isNotEqualTo("1.00 CNY");
    }

    @Test
    void arithmeticRejectsCurrencyMismatch() {
        Money cny = Money.of("1.00", "CNY");
        Money usd = Money.of("1.00", "USD");
        assertThatThrownBy(() -> cny.add(usd))
                .isInstanceOf(CurrencyMismatchException.class)
                .hasMessageContaining("CNY")
                .hasMessageContaining("USD");
        assertThatThrownBy(() -> cny.subtract(usd)).isInstanceOf(CurrencyMismatchException.class);
        assertThatThrownBy(() -> cny.compareTo(usd)).isInstanceOf(CurrencyMismatchException.class);
        assertThatThrownBy(() -> cny.min(usd)).isInstanceOf(CurrencyMismatchException.class);
        assertThatThrownBy(() -> cny.max(usd)).isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void mismatchExceptionExposesCurrencies() {
        CurrencyMismatchException ex = new CurrencyMismatchException("CNY", "USD");
        assertThat(ex.expected()).isEqualTo("CNY");
        assertThat(ex.actual()).isEqualTo("USD");
        assertThat(ex).hasMessageContaining("币种不一致");
        assertThat(new CurrencyMismatchException("CNY", "USD", new IllegalStateException("boom")))
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void nullAmountRejected() {
        assertThatThrownBy(() -> Money.of((BigDecimal) null, "CNY"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Money.of((String) null, "CNY"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Money.of("1.00", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.zero(null)).isInstanceOf(IllegalArgumentException.class);
    }
}

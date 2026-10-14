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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 金额值对象：金额 + 币种，不可变。
 *
 * <p>构造时统一按 {@code setScale(2, HALF_UP)} 保留两位小数。加减比较都要求币种一致，不一致抛 {@link CurrencyMismatchException}。
 */
public final class Money implements Comparable<Money> {

    /** 金额小数位。 */
    private static final int SCALE = 2;

    /** 金额，固定两位小数。 */
    private final BigDecimal amount;

    /** 币种代码，如 CNY、USD。 */
    private final String currency;

    private Money(BigDecimal amount, String currency) {
        this.amount = amount.setScale(SCALE, RoundingMode.HALF_UP);
        this.currency = currency;
    }

    /**
     * 用 {@link BigDecimal} 构造金额。
     *
     * @param amount 金额，null 抛 NullPointerException
     * @param currency 币种，null 或空串抛 IllegalArgumentException
     */
    public static Money of(BigDecimal amount, String currency) {
        Objects.requireNonNull(amount, "amount must not be null");
        return new Money(amount, requireCurrency(currency));
    }

    /**
     * 用字符串构造金额，避免 double 精度问题。
     *
     * @param amount 金额字符串，如 {@code "19.90"}
     * @param currency 币种
     */
    public static Money of(String amount, String currency) {
        Objects.requireNonNull(amount, "amount must not be null");
        return new Money(new BigDecimal(amount), requireCurrency(currency));
    }

    /**
     * 用长整型构造金额。
     *
     * @param amount 金额
     * @param currency 币种
     */
    public static Money of(long amount, String currency) {
        return new Money(BigDecimal.valueOf(amount), requireCurrency(currency));
    }

    /**
     * 指定币种的零元。
     *
     * @param currency 币种
     */
    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, requireCurrency(currency));
    }

    public BigDecimal amount() {
        return amount;
    }

    public String currency() {
        return currency;
    }

    /**
     * 加法。
     *
     * @param other 另一个金额
     * @throws CurrencyMismatchException 币种不一致
     */
    public Money add(Money other) {
        checkCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    /**
     * 减法。
     *
     * @param other 被减金额
     * @throws CurrencyMismatchException 币种不一致
     */
    public Money subtract(Money other) {
        checkCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    /** 取相反数。 */
    public Money negate() {
        return new Money(amount.negate(), currency);
    }

    /**
     * 乘以倍数，结果按两位小数四舍五入。
     *
     * @param factor 倍数
     */
    public Money multiply(BigDecimal factor) {
        Objects.requireNonNull(factor, "factor must not be null");
        return new Money(amount.multiply(factor), currency);
    }

    /**
     * 乘以整数数量。
     *
     * @param quantity 数量
     */
    public Money multiply(int quantity) {
        return multiply(BigDecimal.valueOf(quantity));
    }

    /** 返回两者中较小的一个。 */
    public Money min(Money other) {
        checkCurrency(other);
        return compareTo(other) <= 0 ? this : other;
    }

    /** 返回两者中较大的一个。 */
    public Money max(Money other) {
        checkCurrency(other);
        return compareTo(other) >= 0 ? this : other;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    @Override
    public int compareTo(Money other) {
        checkCurrency(other);
        return amount.compareTo(other.amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Money other)) {
            return false;
        }
        return amount.equals(other.amount) && currency.equals(other.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, currency);
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency;
    }

    private void checkCurrency(Money other) {
        Objects.requireNonNull(other, "money must not be null");
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }

    private static String requireCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
        return currency;
    }
}

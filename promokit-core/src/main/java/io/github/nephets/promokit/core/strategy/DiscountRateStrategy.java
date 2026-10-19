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
package io.github.nephets.promokit.core.strategy;

import io.github.nephets.promokit.core.Discount;
import io.github.nephets.promokit.core.DiscountStrategy;
import io.github.nephets.promokit.core.Money;
import io.github.nephets.promokit.core.Promotion;
import io.github.nephets.promokit.core.PromotionContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 折扣：按折扣率打折，如 0.90 表示九折。可设封顶优惠金额。
 *
 * <p>优惠 = 订单原价 × (1 - 折扣率)，结果两位小数四舍五入。
 */
public final class DiscountRateStrategy implements DiscountStrategy {

    private final BigDecimal rate;
    private final Money maxDiscount;

    /**
     * @param rate 折扣率，取值 (0, 1]
     * @param maxDiscount 封顶优惠金额，null 表示不封顶
     */
    public DiscountRateStrategy(BigDecimal rate, Money maxDiscount) {
        Objects.requireNonNull(rate, "rate must not be null");
        if (rate.compareTo(BigDecimal.ZERO) <= 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("rate must be in (0, 1], got " + rate);
        }
        this.rate = rate;
        this.maxDiscount = maxDiscount;
    }

    public DiscountRateStrategy(BigDecimal rate) {
        this(rate, null);
    }

    /** 用字符串构造折扣率，如 {@code new DiscountRateStrategy("0.9")}。 */
    public static DiscountRateStrategy of(String rate) {
        return new DiscountRateStrategy(new BigDecimal(rate));
    }

    public BigDecimal rate() {
        return rate;
    }

    public Money maxDiscount() {
        return maxDiscount;
    }

    @Override
    public Discount compute(Promotion promotion, PromotionContext ctx) {
        Money subtotal = ctx.cart().subtotal();
        Money raw = subtotal.multiply(BigDecimal.ONE.subtract(rate));
        Money amount = maxDiscount == null ? raw : raw.min(maxDiscount);
        amount = amount.min(subtotal);
        String description = rate.multiply(BigDecimal.TEN).setScale(1, RoundingMode.HALF_UP) + " 折";
        if (maxDiscount != null) {
            description += "，最多减 " + maxDiscount;
        }
        return new Discount(promotion.promotionId(), amount, description);
    }
}

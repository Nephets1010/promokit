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

import java.util.Objects;

/** 券：订单原价达到门槛后减券面额，优惠不超过订单金额。 */
public final class CouponStrategy implements DiscountStrategy {

    private final Money faceValue;
    private final Money threshold;

    /**
     * @param faceValue 券面额
     * @param threshold 使用门槛，null 表示无门槛
     */
    public CouponStrategy(Money faceValue, Money threshold) {
        this.faceValue = Objects.requireNonNull(faceValue, "faceValue must not be null");
        this.threshold = threshold;
    }

    public Money faceValue() {
        return faceValue;
    }

    public Money threshold() {
        return threshold;
    }

    @Override
    public Discount compute(Promotion promotion, PromotionContext ctx) {
        Money subtotal = ctx.cart().subtotal();
        String description =
                threshold == null ? "无门槛券减 " + faceValue : "满 " + threshold + " 用券减 " + faceValue;
        if (threshold != null && subtotal.compareTo(threshold) < 0) {
            return Discount.zero(promotion.promotionId(), subtotal, description + "（未达门槛）");
        }
        return new Discount(promotion.promotionId(), faceValue.min(subtotal), description);
    }
}

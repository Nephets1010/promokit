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

/** 满减：订单原价达到门槛减固定金额，优惠不超过订单金额。 */
public final class FullReductionStrategy implements DiscountStrategy {

    private final Money threshold;
    private final Money reduction;

    public FullReductionStrategy(Money threshold, Money reduction) {
        this.threshold = Objects.requireNonNull(threshold, "threshold must not be null");
        this.reduction = Objects.requireNonNull(reduction, "reduction must not be null");
    }

    public Money threshold() {
        return threshold;
    }

    public Money reduction() {
        return reduction;
    }

    @Override
    public Discount compute(Promotion promotion, PromotionContext ctx) {
        Money subtotal = ctx.cart().subtotal();
        String description = "满 " + threshold + " 减 " + reduction;
        if (subtotal.compareTo(threshold) < 0) {
            return Discount.zero(promotion.promotionId(), subtotal, description + "（未达门槛）");
        }
        return new Discount(promotion.promotionId(), reduction.min(subtotal), description);
    }
}

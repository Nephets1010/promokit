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
package io.github.nephets.promokit.core.condition;

import io.github.nephets.promokit.core.Condition;
import io.github.nephets.promokit.core.Money;
import io.github.nephets.promokit.core.PromotionContext;

import java.util.Objects;

/** 订单满额：购物车原价不低于门槛金额。 */
public final class MinAmountCondition implements Condition {

    private final Money minAmount;

    public MinAmountCondition(Money minAmount) {
        this.minAmount = Objects.requireNonNull(minAmount, "minAmount must not be null");
    }

    public Money minAmount() {
        return minAmount;
    }

    @Override
    public boolean matches(PromotionContext ctx) {
        return ctx.cart().subtotal().compareTo(minAmount) >= 0;
    }

    @Override
    public String describe() {
        return "订单满 " + minAmount;
    }

    @Override
    public String toString() {
        return describe();
    }
}

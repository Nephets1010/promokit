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

import io.github.nephets.promokit.core.CartItem;
import io.github.nephets.promokit.core.Discount;
import io.github.nephets.promokit.core.DiscountStrategy;
import io.github.nephets.promokit.core.Money;
import io.github.nephets.promokit.core.Promotion;
import io.github.nephets.promokit.core.PromotionContext;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 秒杀直降：购物车中命中秒杀价的商品按差价 × 数量累计优惠。
 *
 * <p>秒杀价高于原价时该商品不产生优惠。
 */
public final class FlashSaleStrategy implements DiscountStrategy {

    private final Map<String, Money> flashPrices;

    public FlashSaleStrategy(Map<String, Money> flashPrices) {
        Objects.requireNonNull(flashPrices, "flashPrices must not be null");
        this.flashPrices = Collections.unmodifiableMap(new LinkedHashMap<>(flashPrices));
    }

    public Map<String, Money> flashPrices() {
        return flashPrices;
    }

    @Override
    public Discount compute(Promotion promotion, PromotionContext ctx) {
        Money subtotal = ctx.cart().subtotal();
        Money total = Money.zero(subtotal.currency());
        int hitCount = 0;
        for (CartItem item : ctx.cart().items()) {
            Money flashPrice = flashPrices.get(item.skuId());
            if (flashPrice == null) {
                continue;
            }
            Money diff = item.unitPrice().subtract(flashPrice);
            if (diff.isPositive()) {
                total = total.add(diff.multiply(item.quantity()));
                hitCount++;
            }
        }
        if (hitCount == 0) {
            return Discount.zero(promotion.promotionId(), subtotal, "秒杀商品不在购物车中");
        }
        return new Discount(promotion.promotionId(), total, "秒杀直降 " + hitCount + " 个商品");
    }
}

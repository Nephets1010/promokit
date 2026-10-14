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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 阶梯满减：按订单原价命中的最高档位减。
 *
 * <p>档位在构造时按门槛从高到低排序，取第一个「订单原价 >= 门槛」的档位。
 */
public final class TieredStrategy implements DiscountStrategy {

    /** 一档阶梯：门槛 + 该档优惠。 */
    public static final class Tier {

        private final Money threshold;
        private final Money reduction;

        public Tier(Money threshold, Money reduction) {
            this.threshold = Objects.requireNonNull(threshold, "threshold must not be null");
            this.reduction = Objects.requireNonNull(reduction, "reduction must not be null");
        }

        public static Tier of(Money threshold, Money reduction) {
            return new Tier(threshold, reduction);
        }

        public Money threshold() {
            return threshold;
        }

        public Money reduction() {
            return reduction;
        }

        @Override
        public String toString() {
            return "满 " + threshold + " 减 " + reduction;
        }
    }

    private final List<Tier> tiers;

    public TieredStrategy(List<Tier> tiers) {
        Objects.requireNonNull(tiers, "tiers must not be null");
        if (tiers.isEmpty()) {
            throw new IllegalArgumentException("tiers must not be empty");
        }
        List<Tier> copy = new ArrayList<>(tiers);
        copy.sort(Comparator.comparing(Tier::threshold).reversed());
        this.tiers = List.copyOf(copy);
    }

    public static TieredStrategy of(Tier... tiers) {
        Objects.requireNonNull(tiers, "tiers must not be null");
        return new TieredStrategy(List.of(tiers));
    }

    public List<Tier> tiers() {
        return tiers;
    }

    @Override
    public Discount compute(Promotion promotion, PromotionContext ctx) {
        Money subtotal = ctx.cart().subtotal();
        String description = "阶梯满减：" + tiers;
        for (Tier tier : tiers) {
            if (subtotal.compareTo(tier.threshold()) >= 0) {
                return new Discount(
                        promotion.promotionId(),
                        tier.reduction().min(subtotal),
                        "满 " + tier.threshold() + " 减 " + tier.reduction());
            }
        }
        return Discount.zero(promotion.promotionId(), subtotal, description + "（未达最低档）");
    }
}

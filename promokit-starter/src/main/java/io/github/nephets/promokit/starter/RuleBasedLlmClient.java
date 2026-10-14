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
package io.github.nephets.promokit.starter;

import io.github.nephets.promokit.core.Cart;
import io.github.nephets.promokit.core.CartItem;
import io.github.nephets.promokit.core.LlmClient;
import io.github.nephets.promokit.core.Money;
import io.github.nephets.promokit.core.PromotionType;
import io.github.nephets.promokit.core.SuggestedPromotion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 本地规则版活动建议：按购物车金额给满减档位，跨类目时补一条类目券，目标里提到清库存时补一条秒杀直降。
 *
 * <p>不接任何外部模型，结果只由购物车内容决定，可重复。
 */
public class RuleBasedLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(RuleBasedLlmClient.class);

    /** 门槛相对当前客单价的上浮倍数。 */
    private static final BigDecimal THRESHOLD_FACTOR = new BigDecimal("1.2");

    /** 优惠占门槛的比例。 */
    private static final BigDecimal REDUCTION_FACTOR = new BigDecimal("0.1");

    /** 空购物车时的入门档位。 */
    private static final BigDecimal FALLBACK_THRESHOLD = new BigDecimal("50");

    /** 空购物车时的入门优惠。 */
    private static final BigDecimal FALLBACK_REDUCTION = new BigDecimal("5");

    @Override
    public List<SuggestedPromotion> suggest(Cart cart, String goal) {
        if (cart == null) {
            throw new IllegalArgumentException("cart must not be null");
        }
        String currency = cart.currency();
        Money subtotal = cart.subtotal();
        List<SuggestedPromotion> suggestions = new ArrayList<>();

        Money threshold;
        Money reduction;
        String reason;
        if (subtotal.isZero()) {
            threshold = Money.of(FALLBACK_THRESHOLD, currency);
            reduction = Money.of(FALLBACK_REDUCTION, currency);
            reason = "购物车为空，先给一个入门档位";
        } else {
            threshold =
                    roundTo(
                            subtotal.multiply(THRESHOLD_FACTOR),
                            BigDecimal.TEN,
                            RoundingMode.CEILING);
            reduction =
                    roundTo(
                            threshold.multiply(REDUCTION_FACTOR),
                            BigDecimal.valueOf(5),
                            RoundingMode.FLOOR);
            reason = "当前客单价 " + subtotal + "，门槛抬到 " + threshold + " 可拉高单笔金额";
        }
        suggestions.add(
                new SuggestedPromotion(PromotionType.FULL_REDUCTION, threshold, reduction, reason));

        int categories = distinctCategories(cart);
        if (categories >= 2) {
            suggestions.add(
                    new SuggestedPromotion(
                            PromotionType.COUPON,
                            threshold,
                            Money.of(FALLBACK_REDUCTION, currency),
                            "购物车跨 " + categories + " 个类目，用类目券拉连带"));
        }
        if (goal != null
                && (goal.contains("清仓") || goal.contains("库存") || goal.contains("clearance"))) {
            suggestions.add(
                    new SuggestedPromotion(
                            PromotionType.FLASH_SALE,
                            Money.zero(currency),
                            reduction,
                            "清库存目标，对主力商品做直降"));
        }
        log.debug(
                "suggest returned {} items tenant={} subtotal={} goal={}",
                suggestions.size(),
                cart.tenantId(),
                subtotal,
                goal);
        return suggestions;
    }

    private static int distinctCategories(Cart cart) {
        Set<String> categories = new LinkedHashSet<>();
        for (CartItem item : cart.items()) {
            categories.add(item.categoryId());
        }
        return categories.size();
    }

    private static Money roundTo(Money amount, BigDecimal step, RoundingMode mode) {
        BigDecimal rounded = amount.amount().divide(step, 0, mode).multiply(step);
        return Money.of(rounded, amount.currency());
    }
}

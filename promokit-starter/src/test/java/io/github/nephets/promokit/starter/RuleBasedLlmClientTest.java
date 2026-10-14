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
import io.github.nephets.promokit.core.Money;
import io.github.nephets.promokit.core.PromotionType;
import io.github.nephets.promokit.core.SuggestedPromotion;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleBasedLlmClientTest {

    private static Cart cart(Money... prices) {
        List<CartItem> items = new ArrayList<>();
        for (int i = 0; i < prices.length; i++) {
            items.add(new CartItem("sku-" + i, i == 0 ? "book" : "toy", prices[i], 1));
        }
        return Cart.of("acme", "u1", 0, "APP", items);
    }

    @Test
    void suggestsFullReductionAboveCurrentSubtotal() {
        List<SuggestedPromotion> suggestions =
                new RuleBasedLlmClient().suggest(cart(Money.of("100.00", "CNY")), "提升客单价");
        assertThat(suggestions).isNotEmpty();
        SuggestedPromotion first = suggestions.get(0);
        assertThat(first.type()).isEqualTo(PromotionType.FULL_REDUCTION);
        assertThat(first.threshold()).isEqualTo(Money.of("120.00", "CNY"));
        assertThat(first.reduction()).isEqualTo(Money.of("10.00", "CNY"));
        assertThat(first.reason()).contains("100.00 CNY");
    }

    @Test
    void thresholdsAreRoundedToTenAndReductionsToFive() {
        List<SuggestedPromotion> suggestions =
                new RuleBasedLlmClient().suggest(cart(Money.of("88.00", "CNY")), null);
        assertThat(suggestions.get(0).threshold()).isEqualTo(Money.of("110.00", "CNY"));
        assertThat(suggestions.get(0).reduction()).isEqualTo(Money.of("10.00", "CNY"));
    }

    @Test
    void multiCategoryCartGetsCouponSuggestion() {
        List<SuggestedPromotion> suggestions =
                new RuleBasedLlmClient()
                        .suggest(
                                cart(Money.of("100.00", "CNY"), Money.of("50.00", "CNY")), "提升客单价");
        assertThat(suggestions).hasSize(2);
        assertThat(suggestions.get(1).type()).isEqualTo(PromotionType.COUPON);
        assertThat(suggestions.get(1).reason()).contains("2 个类目");
    }

    @Test
    void clearanceGoalAddsFlashSale() {
        List<SuggestedPromotion> suggestions =
                new RuleBasedLlmClient().suggest(cart(Money.of("100.00", "CNY")), "清库存");
        assertThat(suggestions).hasSize(2);
        assertThat(suggestions.get(1).type()).isEqualTo(PromotionType.FLASH_SALE);
        assertThat(new RuleBasedLlmClient().suggest(cart(Money.of("100.00", "CNY")), "clearance"))
                .hasSize(2);
    }

    @Test
    void emptyCartFallsBackToEntryLevelTier() {
        List<SuggestedPromotion> suggestions = new RuleBasedLlmClient().suggest(cart(), "提升客单价");
        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.get(0).threshold()).isEqualTo(Money.of("50.00", "CNY"));
        assertThat(suggestions.get(0).reduction()).isEqualTo(Money.of("5.00", "CNY"));
        assertThat(suggestions.get(0).reason()).contains("入门档位");
        assertThatThrownBy(() -> new RuleBasedLlmClient().suggest(null, "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

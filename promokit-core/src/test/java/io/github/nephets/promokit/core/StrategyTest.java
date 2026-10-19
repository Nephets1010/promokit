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

import io.github.nephets.promokit.core.strategy.CouponStrategy;
import io.github.nephets.promokit.core.strategy.DiscountRateStrategy;
import io.github.nephets.promokit.core.strategy.FlashSaleStrategy;
import io.github.nephets.promokit.core.strategy.FullReductionStrategy;
import io.github.nephets.promokit.core.strategy.TieredStrategy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrategyTest {

    private static final Promotion PROMOTION =
            Promotion.builder()
                    .promotionId("p1")
                    .tenantId("acme")
                    .name("测试活动")
                    .type(PromotionType.FULL_REDUCTION)
                    .strategy(
                            new FullReductionStrategy(
                                    Money.of("0.00", "CNY"), Money.of("0.00", "CNY")))
                    .window(
                            java.time.Instant.parse("2026-01-01T00:00:00Z"),
                            java.time.Instant.parse("2027-01-01T00:00:00Z"))
                    .build();

    private static PromotionContext ctxOf(Money... prices) {
        List<CartItem> items = new java.util.ArrayList<>();
        for (int i = 0; i < prices.length; i++) {
            items.add(new CartItem("sku-" + i, "book", prices[i], 1));
        }
        return PromotionContext.builder(Cart.of("acme", "u1", 0, "APP", items)).build();
    }

    @Test
    void fullReductionAppliesWhenThresholdMet() {
        FullReductionStrategy strategy =
                new FullReductionStrategy(Money.of("100.00", "CNY"), Money.of("20.00", "CNY"));
        assertThat(strategy.compute(PROMOTION, ctxOf(Money.of("100.00", "CNY"))).amount())
                .isEqualTo(Money.of("20.00", "CNY"));
        assertThat(strategy.compute(PROMOTION, ctxOf(Money.of("99.99", "CNY"))).amount().isZero())
                .isTrue();
        assertThat(strategy.threshold()).isEqualTo(Money.of("100.00", "CNY"));
        assertThat(strategy.reduction()).isEqualTo(Money.of("20.00", "CNY"));
        assertThat(strategy.compute(PROMOTION, ctxOf(Money.of("100.00", "CNY"))).description())
                .isEqualTo("满 100.00 CNY 减 20.00 CNY");
        assertThat(strategy.compute(PROMOTION, ctxOf(Money.of("99.99", "CNY"))).description())
                .contains("未达门槛");
        assertThatThrownBy(() -> new FullReductionStrategy(null, Money.of("1.00", "CNY")))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FullReductionStrategy(Money.of("1.00", "CNY"), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void fullReductionNeverExceedsSubtotal() {
        FullReductionStrategy strategy =
                new FullReductionStrategy(Money.of("10.00", "CNY"), Money.of("50.00", "CNY"));
        assertThat(strategy.compute(PROMOTION, ctxOf(Money.of("20.00", "CNY"))).amount())
                .isEqualTo(Money.of("20.00", "CNY"));
    }

    @Test
    void discountRateComputesPercentageOff() {
        DiscountRateStrategy strategy = DiscountRateStrategy.of("0.90");
        assertThat(strategy.compute(PROMOTION, ctxOf(Money.of("200.00", "CNY"))).amount())
                .isEqualTo(Money.of("20.00", "CNY"));
        assertThat(strategy.compute(PROMOTION, ctxOf(Money.of("200.00", "CNY"))).description())
                .isEqualTo("9.0 折");
        assertThat(strategy.rate()).isEqualByComparingTo("0.90");
        assertThat(strategy.maxDiscount()).isNull();
    }

    @Test
    void discountRateHonoursCap() {
        DiscountRateStrategy strategy =
                new DiscountRateStrategy(new BigDecimal("0.50"), Money.of("30.00", "CNY"));
        assertThat(strategy.compute(PROMOTION, ctxOf(Money.of("200.00", "CNY"))).amount())
                .isEqualTo(Money.of("30.00", "CNY"));
        assertThat(strategy.compute(PROMOTION, ctxOf(Money.of("200.00", "CNY"))).description())
                .contains("最多减");
        assertThat(strategy.maxDiscount()).isEqualTo(Money.of("30.00", "CNY"));
    }

    @Test
    void discountRateRejectsOutOfRangeValues() {
        assertThatThrownBy(() -> new DiscountRateStrategy(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DiscountRateStrategy(new BigDecimal("1.5")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DiscountRateStrategy(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void tieredPicksHighestMatchingTier() {
        TieredStrategy strategy =
                TieredStrategy.of(
                        TieredStrategy.Tier.of(Money.of("100.00", "CNY"), Money.of("10.00", "CNY")),
                        TieredStrategy.Tier.of(Money.of("300.00", "CNY"), Money.of("40.00", "CNY")),
                        TieredStrategy.Tier.of(
                                Money.of("200.00", "CNY"), Money.of("25.00", "CNY")));
        assertThat(strategy.tiers().get(0).threshold()).isEqualTo(Money.of("300.00", "CNY"));
        assertThat(strategy.compute(PROMOTION, ctxOf(Money.of("350.00", "CNY"))).amount())
                .isEqualTo(Money.of("40.00", "CNY"));
        assertThat(strategy.compute(PROMOTION, ctxOf(Money.of("250.00", "CNY"))).amount())
                .isEqualTo(Money.of("25.00", "CNY"));
        assertThat(strategy.compute(PROMOTION, ctxOf(Money.of("150.00", "CNY"))).amount())
                .isEqualTo(Money.of("10.00", "CNY"));
    }

    @Test
    void tieredReturnsZeroBelowLowestTier() {
        TieredStrategy strategy =
                TieredStrategy.of(
                        TieredStrategy.Tier.of(
                                Money.of("100.00", "CNY"), Money.of("10.00", "CNY")));
        Discount discount = strategy.compute(PROMOTION, ctxOf(Money.of("50.00", "CNY")));
        assertThat(discount.amount().isZero()).isTrue();
        assertThat(discount.description()).contains("未达最低档");
        assertThat(TieredStrategy.Tier.of(Money.of("100.00", "CNY"), Money.of("10.00", "CNY")))
                .hasToString("满 100.00 CNY 减 10.00 CNY");
        assertThatThrownBy(() -> new TieredStrategy(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TieredStrategy(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void couponStrategyChecksThreshold() {
        CouponStrategy strategy =
                new CouponStrategy(Money.of("15.00", "CNY"), Money.of("100.00", "CNY"));
        assertThat(strategy.compute(PROMOTION, ctxOf(Money.of("120.00", "CNY"))).amount())
                .isEqualTo(Money.of("15.00", "CNY"));
        assertThat(strategy.compute(PROMOTION, ctxOf(Money.of("80.00", "CNY"))).amount().isZero())
                .isTrue();
        assertThat(strategy.compute(PROMOTION, ctxOf(Money.of("120.00", "CNY"))).description())
                .isEqualTo("满 100.00 CNY 用券减 15.00 CNY");
        assertThat(strategy.faceValue()).isEqualTo(Money.of("15.00", "CNY"));
        assertThat(strategy.threshold()).isEqualTo(Money.of("100.00", "CNY"));
    }

    @Test
    void couponStrategyWithoutThreshold() {
        CouponStrategy strategy = new CouponStrategy(Money.of("15.00", "CNY"), null);
        assertThat(strategy.compute(PROMOTION, ctxOf(Money.of("10.00", "CNY"))).amount())
                .isEqualTo(Money.of("10.00", "CNY"));
        assertThat(strategy.compute(PROMOTION, ctxOf(Money.of("20.00", "CNY"))).description())
                .isEqualTo("无门槛券减 15.00 CNY");
        assertThatThrownBy(() -> new CouponStrategy(null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void flashSaleSumsPriceGaps() {
        Map<String, Money> flashPrices = new LinkedHashMap<>();
        flashPrices.put("sku-0", Money.of("50.00", "CNY"));
        flashPrices.put("sku-1", Money.of("45.00", "CNY"));
        FlashSaleStrategy strategy = new FlashSaleStrategy(flashPrices);
        Discount discount =
                strategy.compute(
                        PROMOTION, ctxOf(Money.of("60.00", "CNY"), Money.of("40.00", "CNY")));
        assertThat(discount.amount()).isEqualTo(Money.of("10.00", "CNY"));
        assertThat(discount.description()).contains("秒杀直降 1 个商品");
        assertThat(strategy.flashPrices()).hasSize(2);
    }

    @Test
    void flashSaleIgnoresMissingOrHigherPrices() {
        FlashSaleStrategy missing = new FlashSaleStrategy(Map.of("sku-9", Money.of("1.00", "CNY")));
        assertThat(missing.compute(PROMOTION, ctxOf(Money.of("60.00", "CNY"))).amount().isZero())
                .isTrue();
        assertThat(missing.compute(PROMOTION, ctxOf(Money.of("60.00", "CNY"))).description())
                .isEqualTo("秒杀商品不在购物车中");
        FlashSaleStrategy higher = new FlashSaleStrategy(Map.of("sku-0", Money.of("99.00", "CNY")));
        assertThat(higher.compute(PROMOTION, ctxOf(Money.of("60.00", "CNY"))).amount().isZero())
                .isTrue();
        assertThatThrownBy(() -> new FlashSaleStrategy(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void discountValueObjectBasics() {
        Discount discount = new Discount("p1", Money.of("5.00", "CNY"), "满减");
        assertThat(discount.isZero()).isFalse();
        assertThat(discount).hasToString("p1 -5.00 CNY (满减)");
        assertThat(discount).isEqualTo(new Discount("p1", Money.of("5.00", "CNY"), "满减"));
        assertThat(discount.hashCode())
                .isEqualTo(new Discount("p1", Money.of("5.00", "CNY"), "满减").hashCode());
        Discount zero = Discount.zero("p1", Money.of("5.00", "CNY"), null);
        assertThat(zero.isZero()).isTrue();
        assertThat(zero.description()).isEmpty();
        assertThatThrownBy(() -> new Discount("p1", null, "x"))
                .isInstanceOf(NullPointerException.class);
    }
}

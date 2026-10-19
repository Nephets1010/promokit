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

import io.github.nephets.promokit.core.strategy.DiscountRateStrategy;
import io.github.nephets.promokit.core.strategy.FullReductionStrategy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 算价金额边界：零金额、负数优惠、恰好等于原价、超过原价一分、极大金额、多币种。 这些都是资损路径，必须逐条锁定。 */
class PriceEngineBoundaryTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-12-31T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");

    private static PromotionContext ctx(Cart cart) {
        return PromotionContext.builder(cart).now(NOW).build();
    }

    private static Cart cart(String unitPrice, int quantity, String currency) {
        List<CartItem> items = new ArrayList<>();
        items.add(new CartItem("sku-1", "book", Money.of(unitPrice, currency), quantity));
        return Cart.of("acme", "u1", 1, "APP", items);
    }

    private static Promotion.Builder builder(
            String id, String name, String threshold, String reduction) {
        return Promotion.builder()
                .promotionId(id)
                .tenantId("acme")
                .name(name)
                .type(PromotionType.FULL_REDUCTION)
                .strategy(
                        new FullReductionStrategy(
                                Money.of(threshold, "CNY"), Money.of(reduction, "CNY")))
                .window(START, END);
    }

    @Test
    void emptyCartDropsEveryPromotion() {
        Cart empty = new Cart("acme", "u1", 1, "APP", "CNY", List.of());
        Promotion promotion = builder("p1", "满1减1", "1.00", "1.00").build();
        PriceResult result = new PriceEngine().quote(List.of(promotion), ctx(empty));
        assertThat(result.subtotal()).isEqualTo(Money.of("0.00", "CNY"));
        assertThat(result.applied()).isEmpty();
        assertThat(result.rejectedReasons().get(0)).contains("优惠金额为 0");
    }

    @Test
    void discountExactlyEqualToSubtotalIsNotTruncated() {
        Promotion promotion = builder("p1", "满1减120", "1.00", "120.00").build();
        PriceResult result =
                new PriceEngine().quote(List.of(promotion), ctx(cart("60.00", 2, "CNY")));
        assertThat(result.totalDiscount()).isEqualTo(Money.of("120.00", "CNY"));
        assertThat(result.payable()).isEqualTo(Money.of("0.00", "CNY"));
        assertThat(result.rejectedReasons()).isEmpty();
    }

    @Test
    void oneCentAboveSubtotalIsTruncatedWithTrace() {
        // 满减策略自带 min(subtotal) 截断，要用能突破原价的策略才能走到引擎级截断。
        Promotion promotion =
                Promotion.builder()
                        .promotionId("p1")
                        .tenantId("acme")
                        .name("固定减120.01")
                        .type(PromotionType.FULL_REDUCTION)
                        .strategy(
                                (p, context) ->
                                        new Discount(
                                                p.promotionId(),
                                                Money.of("120.01", "CNY"),
                                                "固定减 120.01"))
                        .window(START, END)
                        .build();
        PriceResult result =
                new PriceEngine().quote(List.of(promotion), ctx(cart("60.00", 2, "CNY")));
        assertThat(result.totalDiscount()).isEqualTo(Money.of("120.00", "CNY"));
        assertThat(result.payable()).isEqualTo(Money.of("0.00", "CNY"));
        assertThat(result.payable().isNegative()).isFalse();
        assertThat(result.rejectedReasons()).containsExactly("优惠合计超过订单原价，已按原价截断");
    }

    @Test
    void stackedDiscountsBeyondSubtotalAreTruncatedWithTrace() {
        Promotion first = builder("p1", "减80", "1.00", "80.00").build();
        Promotion second = builder("p2", "减70", "1.00", "70.00").build();
        PriceResult result =
                new PriceEngine().quote(List.of(first, second), ctx(cart("60.00", 2, "CNY")));
        assertThat(result.applied()).hasSize(2);
        assertThat(result.totalDiscount()).isEqualTo(Money.of("120.00", "CNY"));
        assertThat(result.payable()).isEqualTo(Money.of("0.00", "CNY"));
        assertThat(result.rejectedReasons()).containsExactly("优惠合计超过订单原价，已按原价截断");
    }

    @Test
    void negativeDiscountStrategyIsDropped() {
        Promotion negative =
                Promotion.builder()
                        .promotionId("p1")
                        .tenantId("acme")
                        .name("倒贴")
                        .type(PromotionType.FULL_REDUCTION)
                        .strategy(
                                (promotion, context) ->
                                        new Discount(
                                                promotion.promotionId(),
                                                Money.of("-5.00", "CNY"),
                                                "负优惠"))
                        .window(START, END)
                        .build();
        PriceResult result =
                new PriceEngine().quote(List.of(negative), ctx(cart("60.00", 1, "CNY")));
        assertThat(result.applied()).isEmpty();
        assertThat(result.rejectedReasons().get(0)).contains("优惠金额为 0");
        assertThat(result.totalDiscount()).isEqualTo(Money.of("0.00", "CNY"));
    }

    @Test
    void hugeAmountsStayExact() {
        Promotion promotion = builder("p1", "大额满减", "1.00", "1.00").build();
        PriceResult result =
                new PriceEngine().quote(List.of(promotion), ctx(cart("999999999999.99", 1, "CNY")));
        assertThat(result.subtotal()).isEqualTo(Money.of("999999999999.99", "CNY"));
        assertThat(result.totalDiscount()).isEqualTo(Money.of("1.00", "CNY"));
        assertThat(result.payable()).isEqualTo(Money.of("999999999998.99", "CNY"));
    }

    @Test
    void nonCnyCurrencyQuotesEndToEnd() {
        Promotion promotion =
                Promotion.builder()
                        .promotionId("p1")
                        .tenantId("acme")
                        .name("USD 满减")
                        .type(PromotionType.FULL_REDUCTION)
                        .strategy(
                                new FullReductionStrategy(
                                        Money.of("100.00", "USD"), Money.of("20.00", "USD")))
                        .window(START, END)
                        .build();
        PriceResult result =
                new PriceEngine().quote(List.of(promotion), ctx(cart("60.00", 2, "USD")));
        assertThat(result.subtotal().currency()).isEqualTo("USD");
        assertThat(result.totalDiscount()).isEqualTo(Money.of("20.00", "USD"));
        assertThat(result.payable()).isEqualTo(Money.of("100.00", "USD"));
    }

    @Test
    void tenStackablePromotionsCapAtDefaultDepth() {
        List<Promotion> promotions = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            promotions.add(builder("p" + i, "减" + i, "1.00", i + ".00").build());
        }
        PriceResult result = new PriceEngine().quote(promotions, ctx(cart("60.00", 2, "CNY")));
        assertThat(result.applied()).hasSize(3);
        assertThat(result.totalDiscount()).isEqualTo(Money.of("27.00", "CNY"));
        // 深度截断发生在组合比价阶段，只保留从优到劣的前 maxStackDepth 个，不写拒绝原因。
        assertThat(result.rejectedReasons()).isEmpty();
    }

    @Test
    void rateStrategyRoundingIsDeterministic() {
        Promotion rate =
                Promotion.builder()
                        .promotionId("p1")
                        .tenantId("acme")
                        .name("95折")
                        .type(PromotionType.DISCOUNT)
                        .strategy(new DiscountRateStrategy(new BigDecimal("0.95")))
                        .window(START, END)
                        .build();
        PriceResult result = new PriceEngine().quote(List.of(rate), ctx(cart("33.33", 1, "CNY")));
        // 33.33 * 0.05 = 1.6665 → 两位小数四舍五入 1.67。
        assertThat(result.totalDiscount()).isEqualTo(Money.of("1.67", "CNY"));
        assertThat(result.payable()).isEqualTo(Money.of("31.66", "CNY"));
    }

    @Test
    void nullsInPromotionListAreSkippedWithReason() {
        List<Promotion> promotions = new ArrayList<>();
        promotions.add(null);
        promotions.add(builder("p1", "减10", "1.00", "10.00").build());
        promotions.add(null);
        PriceResult result = new PriceEngine().quote(promotions, ctx(cart("60.00", 1, "CNY")));
        assertThat(result.applied()).hasSize(1);
        assertThat(result.rejectedReasons())
                .filteredOn(reason -> reason.equals("活动列表含 null，已跳过"))
                .hasSize(2);
    }

    @Test
    void invariantHoldsAcrossMixedCombinations() {
        Promotion rate =
                Promotion.builder()
                        .promotionId("rate")
                        .tenantId("acme")
                        .name("88折")
                        .type(PromotionType.DISCOUNT)
                        .strategy(new DiscountRateStrategy(new BigDecimal("0.88")))
                        .window(START, END)
                        .build();
        Promotion reduction = builder("flat", "满100减30", "100.00", "30.00").build();
        Promotion exclusive = builder("solo", "独占减88", "1.00", "88.00").stackable(false).build();
        PriceResult result =
                new PriceEngine()
                        .quote(List.of(rate, reduction, exclusive), ctx(cart("45.00", 3, "CNY")));
        assertThat(result.payable()).isEqualTo(result.subtotal().subtract(result.totalDiscount()));
        assertThat(result.payable().isNegative()).isFalse();
        assertThat(result.totalDiscount().compareTo(result.subtotal()) <= 0).isTrue();
    }
}

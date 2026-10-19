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

import io.github.nephets.promokit.core.condition.MinAmountCondition;
import io.github.nephets.promokit.core.strategy.DiscountRateStrategy;
import io.github.nephets.promokit.core.strategy.FullReductionStrategy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PriceEngineTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-12-31T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");

    private static Cart cartOf(int quantity, Money unitPrice) {
        List<CartItem> items = new ArrayList<>();
        items.add(new CartItem("sku-1", "book", unitPrice, quantity));
        return Cart.of("acme", "u1", 1, "APP", items);
    }

    private static PromotionContext ctx(Cart cart) {
        return PromotionContext.builder(cart).now(NOW).build();
    }

    private static Promotion.Builder builder(String id, String name, DiscountStrategy strategy) {
        return Promotion.builder()
                .promotionId(id)
                .tenantId("acme")
                .name(name)
                .type(PromotionType.FULL_REDUCTION)
                .strategy(strategy)
                .window(START, END);
    }

    private static DiscountStrategy reduce(Money threshold, Money amount) {
        return new FullReductionStrategy(threshold, amount);
    }

    @Test
    void emptyPromotionListReturnsSubtotal() {
        PriceResult result =
                new PriceEngine().quote(List.of(), ctx(cartOf(2, Money.of("60.00", "CNY"))));
        assertThat(result.subtotal()).isEqualTo(Money.of("120.00", "CNY"));
        assertThat(result.totalDiscount()).isEqualTo(Money.of("0.00", "CNY"));
        assertThat(result.payable()).isEqualTo(Money.of("120.00", "CNY"));
        assertThat(result.hasDiscount()).isFalse();
        assertThat(result.rejectedReasons()).isEmpty();
    }

    @Test
    void singlePromotionIsApplied() {
        Promotion promotion =
                builder(
                                "p1",
                                "满100减20",
                                reduce(Money.of("100.00", "CNY"), Money.of("20.00", "CNY")))
                        .build();
        PriceResult result =
                new PriceEngine()
                        .quote(List.of(promotion), ctx(cartOf(2, Money.of("60.00", "CNY"))));
        assertThat(result.totalDiscount()).isEqualTo(Money.of("20.00", "CNY"));
        assertThat(result.payable()).isEqualTo(Money.of("100.00", "CNY"));
        assertThat(result.applied()).hasSize(1);
        assertThat(result.applied().get(0).promotionId()).isEqualTo("p1");
    }

    @Test
    void pausedAndEndedPromotionsAreRejected() {
        Promotion paused =
                builder("p1", "暂停", reduce(Money.of("1.00", "CNY"), Money.of("5.00", "CNY")))
                        .status(PromotionStatus.PAUSED)
                        .build();
        Promotion draft =
                builder("p2", "草稿", reduce(Money.of("1.00", "CNY"), Money.of("5.00", "CNY")))
                        .status(PromotionStatus.DRAFT)
                        .build();
        PriceResult result =
                new PriceEngine()
                        .quote(List.of(paused, draft), ctx(cartOf(1, Money.of("60.00", "CNY"))));
        assertThat(result.applied()).isEmpty();
        assertThat(result.rejectedReasons()).hasSize(2);
        assertThat(result.rejectedReasons().get(0)).contains("状态为 PAUSED");
    }

    @Test
    void promotionsOutsideWindowAreRejected() {
        Promotion notStarted =
                builder("p1", "未开始", reduce(Money.of("1.00", "CNY"), Money.of("5.00", "CNY")))
                        .window(NOW.plusSeconds(10), NOW.plusSeconds(20))
                        .build();
        Promotion ended =
                builder("p2", "已结束", reduce(Money.of("1.00", "CNY"), Money.of("5.00", "CNY")))
                        .window(START, NOW.minusSeconds(1))
                        .build();
        PriceResult result =
                new PriceEngine()
                        .quote(
                                List.of(notStarted, ended),
                                ctx(cartOf(1, Money.of("60.00", "CNY"))));
        assertThat(result.applied()).isEmpty();
        assertThat(result.rejectedReasons()).anyMatch(reason -> reason.contains("活动未开始"));
        assertThat(result.rejectedReasons()).anyMatch(reason -> reason.contains("活动已结束"));
    }

    @Test
    void otherTenantPromotionsAreRejected() {
        Promotion other =
                builder("p1", "别家活动", reduce(Money.of("1.00", "CNY"), Money.of("5.00", "CNY")))
                        .tenantId("globex")
                        .build();
        PriceResult result =
                new PriceEngine().quote(List.of(other), ctx(cartOf(1, Money.of("60.00", "CNY"))));
        assertThat(result.applied()).isEmpty();
        assertThat(result.rejectedReasons().get(0)).contains("不属于租户 acme");
    }

    @Test
    void unmatchedConditionIsRejectedWithReason() {
        Promotion promotion =
                builder("p1", "满200减30", reduce(Money.of("1.00", "CNY"), Money.of("30.00", "CNY")))
                        .condition(new MinAmountCondition(Money.of("200.00", "CNY")))
                        .build();
        PriceResult result =
                new PriceEngine()
                        .quote(List.of(promotion), ctx(cartOf(1, Money.of("60.00", "CNY"))));
        assertThat(result.applied()).isEmpty();
        assertThat(result.rejectedReasons().get(0)).contains("未满足门槛：订单满 200.00 CNY");
    }

    @Test
    void zeroDiscountPromotionIsDropped() {
        Promotion promotion =
                builder(
                                "p1",
                                "满1000减5",
                                reduce(Money.of("1000.00", "CNY"), Money.of("5.00", "CNY")))
                        .build();
        PriceResult result =
                new PriceEngine()
                        .quote(List.of(promotion), ctx(cartOf(1, Money.of("60.00", "CNY"))));
        assertThat(result.applied()).isEmpty();
        assertThat(result.rejectedReasons().get(0)).contains("优惠金额为 0");

        List<Promotion> withNull = new ArrayList<>();
        withNull.add(null);
        PriceResult nullResult =
                new PriceEngine().quote(withNull, ctx(cartOf(1, Money.of("60.00", "CNY"))));
        assertThat(nullResult.applied()).isEmpty();
        assertThat(nullResult.rejectedReasons()).containsExactly("活动列表含 null，已跳过");
    }

    @Test
    void stackablePromotionsAreCombined() {
        Promotion first =
                builder(
                                "p1",
                                "满100减20",
                                reduce(Money.of("100.00", "CNY"), Money.of("20.00", "CNY")))
                        .build();
        Promotion second =
                builder("p2", "满50减5", reduce(Money.of("50.00", "CNY"), Money.of("5.00", "CNY")))
                        .build();
        PriceResult result =
                new PriceEngine()
                        .quote(List.of(first, second), ctx(cartOf(2, Money.of("60.00", "CNY"))));
        assertThat(result.totalDiscount()).isEqualTo(Money.of("25.00", "CNY"));
        assertThat(result.payable()).isEqualTo(Money.of("95.00", "CNY"));
        assertThat(result.applied()).hasSize(2);
    }

    @Test
    void maxStackDepthLimitsCombination() {
        List<Promotion> promotions = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            promotions.add(
                    builder(
                                    "p" + i,
                                    "减" + i,
                                    reduce(
                                            Money.of("1.00", "CNY"),
                                            Money.of(String.valueOf(i), "CNY")))
                            .build());
        }
        PriceResult result =
                new PriceEngine(null, 2)
                        .quote(promotions, ctx(cartOf(2, Money.of("60.00", "CNY"))));
        assertThat(result.applied()).hasSize(2);
        assertThat(result.totalDiscount()).isEqualTo(Money.of("7.00", "CNY"));
    }

    @Test
    void exclusionGroupKeepsTheBiggestDiscount() {
        Promotion small =
                builder("p1", "减10", reduce(Money.of("1.00", "CNY"), Money.of("10.00", "CNY")))
                        .exclusionGroup("base")
                        .build();
        Promotion big =
                builder("p2", "减30", reduce(Money.of("1.00", "CNY"), Money.of("30.00", "CNY")))
                        .exclusionGroup("base")
                        .build();
        PriceResult result =
                new PriceEngine()
                        .quote(List.of(small, big), ctx(cartOf(2, Money.of("60.00", "CNY"))));
        assertThat(result.applied()).hasSize(1);
        assertThat(result.applied().get(0).promotionId()).isEqualTo("p2");
        assertThat(result.rejectedReasons()).anyMatch(reason -> reason.contains("互斥组 [base]"));
    }

    @Test
    void exclusionGroupTieUsesLowerPriority() {
        Promotion highPriority =
                builder("p1", "A", reduce(Money.of("1.00", "CNY"), Money.of("10.00", "CNY")))
                        .exclusionGroup("base")
                        .priority(5)
                        .build();
        Promotion lowPriority =
                builder("p2", "B", reduce(Money.of("1.00", "CNY"), Money.of("10.00", "CNY")))
                        .exclusionGroup("base")
                        .priority(1)
                        .build();
        PriceResult result =
                new PriceEngine()
                        .quote(
                                List.of(highPriority, lowPriority),
                                ctx(cartOf(2, Money.of("60.00", "CNY"))));
        assertThat(result.applied().get(0).promotionId()).isEqualTo("p2");
    }

    @Test
    void nonStackablePromotionWinsAloneWhenBigger() {
        Promotion exclusive =
                builder("p1", "独占减40", reduce(Money.of("1.00", "CNY"), Money.of("40.00", "CNY")))
                        .stackable(false)
                        .build();
        Promotion stackable =
                builder("p2", "减10", reduce(Money.of("1.00", "CNY"), Money.of("10.00", "CNY")))
                        .build();
        PriceResult result =
                new PriceEngine()
                        .quote(
                                List.of(exclusive, stackable),
                                ctx(cartOf(2, Money.of("60.00", "CNY"))));
        assertThat(result.applied()).hasSize(1);
        assertThat(result.applied().get(0).promotionId()).isEqualTo("p1");
        assertThat(result.totalDiscount()).isEqualTo(Money.of("40.00", "CNY"));
    }

    @Test
    void stackableCombinationWinsWhenBigger() {
        Promotion exclusive =
                builder("p1", "独占减10", reduce(Money.of("1.00", "CNY"), Money.of("10.00", "CNY")))
                        .stackable(false)
                        .build();
        Promotion first =
                builder("p2", "减20", reduce(Money.of("1.00", "CNY"), Money.of("20.00", "CNY")))
                        .build();
        Promotion second =
                builder("p3", "减15", reduce(Money.of("1.00", "CNY"), Money.of("15.00", "CNY")))
                        .build();
        PriceResult result =
                new PriceEngine()
                        .quote(
                                List.of(exclusive, first, second),
                                ctx(cartOf(2, Money.of("60.00", "CNY"))));
        assertThat(result.applied()).hasSize(2);
        assertThat(result.totalDiscount()).isEqualTo(Money.of("35.00", "CNY"));
    }

    @Test
    void quotaIsReservedForEachAppliedPromotion() {
        Promotion promotion =
                builder("p1", "限量减20", reduce(Money.of("1.00", "CNY"), Money.of("20.00", "CNY")))
                        .totalQuota(2)
                        .perUserQuota(1)
                        .build();
        InMemoryQuotaStore store = new InMemoryQuotaStore();
        store.registerQuota(promotion);
        PriceEngine engine = new PriceEngine(store);
        PromotionContext ctx = ctx(cartOf(2, Money.of("60.00", "CNY")));
        assertThat(engine.quote(List.of(promotion), ctx).applied()).hasSize(1);
        assertThat(store.usedTotal("acme", "p1")).isEqualTo(1);
        assertThat(engine.quote(List.of(promotion), ctx).applied()).isEmpty();
        assertThat(engine.quote(List.of(promotion), ctx).rejectedReasons())
                .anyMatch(reason -> reason.contains("名额不足"));
    }

    @Test
    void perUserQuotaBlocksSameUserOnly() {
        Promotion promotion =
                builder("p1", "每人限1次", reduce(Money.of("1.00", "CNY"), Money.of("20.00", "CNY")))
                        .perUserQuota(1)
                        .build();
        InMemoryQuotaStore store = new InMemoryQuotaStore();
        store.registerQuota(promotion);
        PriceEngine engine = new PriceEngine(store);
        Cart cart = cartOf(2, Money.of("60.00", "CNY"));
        assertThat(engine.quote(List.of(promotion), ctx(cart)).applied()).hasSize(1);
        PromotionContext otherUser = PromotionContext.builder(cart).now(NOW).userId("u2").build();
        assertThat(engine.quote(List.of(promotion), otherUser).applied()).hasSize(1);
        assertThat(store.usedByUser("acme", "p1", "u1")).isEqualTo(1);
        assertThat(store.usedByUser("acme", "p1", "u2")).isEqualTo(1);
    }

    @Test
    void reserveFailureRollsBackEarlierReservations() {
        Promotion first =
                builder("p1", "减20", reduce(Money.of("1.00", "CNY"), Money.of("20.00", "CNY")))
                        .totalQuota(5)
                        .build();
        Promotion second =
                builder("p2", "减10", reduce(Money.of("1.00", "CNY"), Money.of("10.00", "CNY")))
                        .totalQuota(5)
                        .build();
        InMemoryQuotaStore store = new InMemoryQuotaStore();
        store.registerQuota(first);
        store.registerQuota(second);
        QuotaStore failingOnSecond =
                new QuotaStore() {
                    @Override
                    public boolean reserve(String tenantId, String promotionId, String userId) {
                        return !promotionId.equals("p2")
                                && store.reserve(tenantId, promotionId, userId);
                    }

                    @Override
                    public void confirm(String tenantId, String promotionId, String userId) {
                        store.confirm(tenantId, promotionId, userId);
                    }

                    @Override
                    public void release(String tenantId, String promotionId, String userId) {
                        store.release(tenantId, promotionId, userId);
                    }

                    @Override
                    public int usedByUser(String tenantId, String promotionId, String userId) {
                        return store.usedByUser(tenantId, promotionId, userId);
                    }

                    @Override
                    public int usedTotal(String tenantId, String promotionId) {
                        return store.usedTotal(tenantId, promotionId);
                    }
                };
        PriceResult result =
                new PriceEngine(failingOnSecond)
                        .quote(List.of(first, second), ctx(cartOf(2, Money.of("60.00", "CNY"))));
        assertThat(result.applied()).isEmpty();
        assertThat(result.rejectedReasons()).anyMatch(reason -> reason.contains("名额不足"));
        assertThat(store.usedTotal("acme", "p1")).isZero();
    }

    @Test
    void totalDiscountIsCappedAtSubtotal() {
        Promotion huge =
                builder(
                                "p1",
                                "减200",
                                (promotion, context) ->
                                        new Discount(
                                                promotion.promotionId(),
                                                Money.of("200.00", "CNY"),
                                                "固定减 200"))
                        .build();
        PriceResult result =
                new PriceEngine().quote(List.of(huge), ctx(cartOf(1, Money.of("60.00", "CNY"))));
        assertThat(result.subtotal()).isEqualTo(Money.of("60.00", "CNY"));
        assertThat(result.totalDiscount()).isEqualTo(Money.of("60.00", "CNY"));
        assertThat(result.payable()).isEqualTo(Money.of("0.00", "CNY"));
        assertThat(result.payable().isNegative()).isFalse();
        assertThat(result.rejectedReasons()).anyMatch(reason -> reason.contains("按原价截断"));
    }

    @Test
    void subtotalMinusDiscountAlwaysEqualsPayable() {
        Promotion discountRate =
                builder("p1", "9折", new DiscountRateStrategy(new BigDecimal("0.90"))).build();
        Promotion fullReduction =
                builder(
                                "p2",
                                "满100减20",
                                reduce(Money.of("100.00", "CNY"), Money.of("20.00", "CNY")))
                        .build();
        PriceResult result =
                new PriceEngine()
                        .quote(
                                List.of(discountRate, fullReduction),
                                ctx(cartOf(3, Money.of("45.00", "CNY"))));
        assertThat(result.payable()).isEqualTo(result.subtotal().subtract(result.totalDiscount()));
        assertThat(result).hasToString("PriceResult{原价 135.00 CNY, 优惠 33.50 CNY, 应付 101.50 CNY}");
    }

    @Test
    void constructorValidatesInputs() {
        assertThatThrownBy(() -> new PriceEngine(null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxStackDepth");
        assertThatThrownBy(
                        () ->
                                new PriceEngine()
                                        .quote(null, ctx(cartOf(1, Money.of("60.00", "CNY")))))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PriceEngine().quote(List.of(), null))
                .isInstanceOf(NullPointerException.class);
        assertThat(new PriceEngine().maxStackDepth()).isEqualTo(3);
        assertThat(new PriceEngine(new InMemoryQuotaStore()).quotaStore()).isNotNull();
    }

    @Test
    void blankExclusionGroupIsTreatedAsUngrouped() {
        Promotion first =
                builder("p1", "减20", reduce(Money.of("1.00", "CNY"), Money.of("20.00", "CNY")))
                        .exclusionGroup(" ")
                        .build();
        Promotion second =
                builder("p2", "减10", reduce(Money.of("1.00", "CNY"), Money.of("10.00", "CNY")))
                        .exclusionGroup(" ")
                        .build();
        PriceResult result =
                new PriceEngine()
                        .quote(List.of(first, second), ctx(cartOf(2, Money.of("60.00", "CNY"))));
        assertThat(result.applied()).hasSize(2);
    }
}

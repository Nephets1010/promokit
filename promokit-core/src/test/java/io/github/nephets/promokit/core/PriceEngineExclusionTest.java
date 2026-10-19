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

/** 互斥组裁决与组合比价的边界矩阵：每个组合至少锁定一条回归，防止「同组多放行」「独占/叠加比错」这类资损路径。 */
class PriceEngineExclusionTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-12-31T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");

    private static PromotionContext ctx() {
        return PromotionContext.builder(cart()).now(NOW).build();
    }

    private static Cart cart() {
        List<CartItem> items = new ArrayList<>();
        items.add(new CartItem("sku-1", "book", Money.of("60.00", "CNY"), 2));
        return Cart.of("acme", "u1", 1, "APP", items);
    }

    private static Promotion.Builder builder(String id, String name, int amount) {
        return Promotion.builder()
                .promotionId(id)
                .tenantId("acme")
                .name(name)
                .type(PromotionType.FULL_REDUCTION)
                .strategy(
                        new FullReductionStrategy(
                                Money.of("1.00", "CNY"), Money.of(amount + ".00", "CNY")))
                .window(START, END);
    }

    @Test
    void threeMembersInOneGroupKeepOnlyTheBiggest() {
        List<Promotion> promotions =
                List.of(
                        builder("p1", "减10", 10).exclusionGroup("base").build(),
                        builder("p2", "减30", 30).exclusionGroup("base").build(),
                        builder("p3", "减20", 20).exclusionGroup("base").build());
        PriceResult result = new PriceEngine().quote(promotions, ctx());
        assertThat(result.applied()).hasSize(1);
        assertThat(result.applied().get(0).promotionId()).isEqualTo("p2");
        assertThat(result.rejectedReasons())
                .anyMatch(reason -> reason.contains("p1") && reason.contains("互斥组 [base]"))
                .anyMatch(reason -> reason.contains("p3") && reason.contains("互斥组 [base]"));
    }

    @Test
    void equalAmountAndPriorityFallsBackToLowerId() {
        List<Promotion> promotions =
                List.of(
                        builder("p9", "A", 10).exclusionGroup("base").priority(2).build(),
                        builder("p2", "B", 10).exclusionGroup("base").priority(2).build());
        PriceResult result = new PriceEngine().quote(promotions, ctx());
        assertThat(result.applied().get(0).promotionId()).isEqualTo("p2");
    }

    @Test
    void twoGroupsResolveIndependently() {
        List<Promotion> promotions =
                List.of(
                        builder("p1", "g1 小", 10).exclusionGroup("g1").build(),
                        builder("p2", "g1 大", 20).exclusionGroup("g1").build(),
                        builder("p3", "g2 小", 5).exclusionGroup("g2").build(),
                        builder("p4", "g2 大", 15).exclusionGroup("g2").build());
        PriceResult result = new PriceEngine().quote(promotions, ctx());
        assertThat(result.applied()).hasSize(2);
        assertThat(result.applied().get(0).promotionId()).isEqualTo("p2");
        assertThat(result.applied().get(1).promotionId()).isEqualTo("p4");
        assertThat(result.totalDiscount()).isEqualTo(Money.of("35.00", "CNY"));
    }

    @Test
    void groupWinnerStillCompetesInCombination() {
        // 互斥组胜出者回到组合比价：和可叠加活动一起按总优惠竞争。
        Promotion groupWinner = builder("p1", "组内胜者", 25).exclusionGroup("base").build();
        Promotion groupLoser = builder("p2", "组内败者", 20).exclusionGroup("base").build();
        Promotion stackable = builder("p3", "可叠加", 30).build();
        PriceResult result =
                new PriceEngine().quote(List.of(groupWinner, groupLoser, stackable), ctx());
        assertThat(result.applied()).hasSize(2);
        assertThat(result.applied().get(0).promotionId()).isEqualTo("p3");
        assertThat(result.applied().get(1).promotionId()).isEqualTo("p1");
        assertThat(result.totalDiscount()).isEqualTo(Money.of("55.00", "CNY"));
    }

    @Test
    void exclusiveEqualToStackSumWinsAlone() {
        // 相等时取独占（>=）：独占配置更简单，资损面更小。
        Promotion exclusive = builder("p1", "独占", 30).stackable(false).build();
        Promotion first = builder("p2", "叠加A", 20).build();
        Promotion second = builder("p3", "叠加B", 10).build();
        PriceResult result = new PriceEngine().quote(List.of(exclusive, first, second), ctx());
        assertThat(result.applied()).hasSize(1);
        assertThat(result.applied().get(0).promotionId()).isEqualTo("p1");
    }

    @Test
    void exclusiveOneYuanBelowStackSumLoses() {
        Promotion exclusive = builder("p1", "独占", 29).stackable(false).build();
        Promotion first = builder("p2", "叠加A", 20).build();
        Promotion second = builder("p3", "叠加B", 10).build();
        PriceResult result = new PriceEngine().quote(List.of(exclusive, first, second), ctx());
        assertThat(result.applied()).hasSize(2);
        assertThat(result.totalDiscount()).isEqualTo(Money.of("30.00", "CNY"));
    }

    @Test
    void onlyExclusiveCandidatesReturnTheBestSolo() {
        Promotion small = builder("p1", "独占小", 10).stackable(false).build();
        Promotion big = builder("p2", "独占大", 40).stackable(false).build();
        PriceResult result = new PriceEngine().quote(List.of(small, big), ctx());
        assertThat(result.applied()).hasSize(1);
        assertThat(result.applied().get(0).promotionId()).isEqualTo("p2");
    }

    @Test
    void stackDepthOneKeepsOnlyTheBestStackable() {
        Promotion first = builder("p1", "大", 30).build();
        Promotion second = builder("p2", "中", 20).build();
        Promotion third = builder("p3", "小", 10).build();
        PriceResult result = new PriceEngine(null, 1).quote(List.of(first, second, third), ctx());
        assertThat(result.applied()).hasSize(1);
        assertThat(result.applied().get(0).promotionId()).isEqualTo("p1");
        // 深度截断只保留最优的一个，按 PriceEngineBoundaryTest 的口径约定不写拒绝原因。
        assertThat(result.rejectedReasons()).isEmpty();
    }

    @Test
    void stackDepthTrimKeepsBiggestFirst() {
        List<Promotion> promotions = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            promotions.add(builder("p" + i, "减" + i, i).build());
        }
        PriceResult result = new PriceEngine(null, 3).quote(promotions, ctx());
        assertThat(result.applied()).hasSize(3);
        assertThat(result.applied().stream().map(Discount::promotionId))
                .containsExactly("p4", "p3", "p2");
    }

    @Test
    void appliedOrderFollowsBestFirstComparator() {
        Promotion rate =
                Promotion.builder()
                        .promotionId("rate")
                        .tenantId("acme")
                        .name("9折")
                        .type(PromotionType.DISCOUNT)
                        .strategy(new DiscountRateStrategy(new BigDecimal("0.90")))
                        .window(START, END)
                        .build();
        Promotion reduction = builder("flat", "满减", 10).build();
        PriceResult result = new PriceEngine().quote(List.of(reduction, rate), ctx());
        // 原价 120：折扣减 12.00 > 满减 10.00，折扣排前。
        assertThat(result.applied().get(0).promotionId()).isEqualTo("rate");
        assertThat(result.applied().get(1).promotionId()).isEqualTo("flat");
    }

    @Test
    void ungroupedAndGroupedMixKeepsAllWinners() {
        Promotion ungrouped = builder("p1", "无组", 10).build();
        Promotion blankGroup = builder("p2", "空组名", 20).exclusionGroup("  ").build();
        Promotion grouped = builder("p3", "有组", 30).exclusionGroup("base").build();
        PriceResult result =
                new PriceEngine().quote(List.of(ungrouped, blankGroup, grouped), ctx());
        assertThat(result.applied()).hasSize(3);
        assertThat(result.totalDiscount()).isEqualTo(Money.of("60.00", "CNY"));
    }
}

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

import io.github.nephets.promokit.core.condition.AndCondition;
import io.github.nephets.promokit.core.condition.CategoryInCondition;
import io.github.nephets.promokit.core.condition.ChannelCondition;
import io.github.nephets.promokit.core.condition.MemberLevelCondition;
import io.github.nephets.promokit.core.condition.MinAmountCondition;
import io.github.nephets.promokit.core.condition.OrCondition;
import io.github.nephets.promokit.core.condition.SkuInCondition;
import io.github.nephets.promokit.core.strategy.DiscountRateStrategy;
import io.github.nephets.promokit.core.strategy.FullReductionStrategy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 跨特性组合回归：把门槛条件、互斥组、名额、叠加深度、租户隔离、窗口边界放在同一笔算价里，锁住「部分特性叠加时」的资损路径。 已有的 PriceEngineTest /
 * PriceEngineExclusionTest / PriceEngineQuotaTest / PriceEngineBoundaryTest 各自只覆盖单特性，这里补组合。
 */
class PriceEngineScenarioTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-12-31T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");

    private static Cart cart() {
        List<CartItem> items = new ArrayList<>();
        items.add(new CartItem("sku-1", "book", Money.of("60.00", "CNY"), 2));
        return Cart.of("acme", "u1", 1, "APP", items);
    }

    private static PromotionContext ctx() {
        return PromotionContext.builder(cart()).now(NOW).memberLevel(1).build();
    }

    private static Promotion.Builder fullReduction(String id, int amount) {
        return Promotion.builder()
                .promotionId(id)
                .tenantId("acme")
                .name("减" + amount)
                .type(PromotionType.FULL_REDUCTION)
                .strategy(
                        new FullReductionStrategy(
                                Money.of("1.00", "CNY"), Money.of(amount + ".00", "CNY")))
                .window(START, END);
    }

    private static Promotion rate(String id, String rate) {
        return Promotion.builder()
                .promotionId(id)
                .tenantId("acme")
                .name(id)
                .type(PromotionType.DISCOUNT)
                .strategy(new DiscountRateStrategy(new BigDecimal(rate)))
                .window(START, END)
                .build();
    }

    @Test
    void andConditionRejectsWhenAnyClauseFails() {
        Promotion pass =
                fullReduction("p1", 20)
                        .condition(
                                AndCondition.of(
                                        new MinAmountCondition(Money.of("100.00", "CNY")),
                                        new MemberLevelCondition(1)))
                        .build();
        Promotion fail =
                fullReduction("p2", 20)
                        .condition(
                                AndCondition.of(
                                        new MinAmountCondition(Money.of("100.00", "CNY")),
                                        new MemberLevelCondition(9)))
                        .build();
        PriceResult result = new PriceEngine().quote(List.of(pass, fail), ctx());
        assertThat(result.applied()).hasSize(1);
        assertThat(result.applied().get(0).promotionId()).isEqualTo("p1");
        assertThat(result.rejectedReasons()).anyMatch(r -> r.contains("p2") && r.contains("会员等级"));
    }

    @Test
    void orConditionMatchesWhenAnyClausePasses() {
        Promotion pass =
                fullReduction("p1", 20)
                        .condition(
                                OrCondition.of(
                                        new MemberLevelCondition(9),
                                        new MinAmountCondition(Money.of("100.00", "CNY"))))
                        .build();
        Promotion fail =
                fullReduction("p2", 20)
                        .condition(
                                OrCondition.of(
                                        new MemberLevelCondition(9),
                                        new MinAmountCondition(Money.of("200.00", "CNY"))))
                        .build();
        PriceResult result = new PriceEngine().quote(List.of(pass, fail), ctx());
        assertThat(result.applied()).hasSize(1);
        assertThat(result.applied().get(0).promotionId()).isEqualTo("p1");
        assertThat(result.rejectedReasons()).anyMatch(r -> r.contains("p2") && r.contains("会员等级"));
    }

    @Test
    void categoryConditionRejectionMentionsCategory() {
        Promotion fail = fullReduction("p1", 20).condition(CategoryInCondition.of("food")).build();
        PriceResult result = new PriceEngine().quote(List.of(fail), ctx());
        assertThat(result.applied()).isEmpty();
        assertThat(result.rejectedReasons())
                .anyMatch(r -> r.contains("p1") && r.contains("指定类目 food"));
    }

    @Test
    void categoryConditionMatchesWhenCartHasCategory() {
        Promotion pass = fullReduction("p1", 20).condition(CategoryInCondition.of("book")).build();
        PriceResult result = new PriceEngine().quote(List.of(pass), ctx());
        assertThat(result.applied()).hasSize(1);
        assertThat(result.applied().get(0).promotionId()).isEqualTo("p1");
    }

    @Test
    void skuConditionRejectionMentionsSku() {
        Promotion fail = fullReduction("p1", 20).condition(SkuInCondition.of("sku-9")).build();
        PriceResult result = new PriceEngine().quote(List.of(fail), ctx());
        assertThat(result.applied()).isEmpty();
        assertThat(result.rejectedReasons())
                .anyMatch(r -> r.contains("p1") && r.contains("指定商品 sku-9"));
    }

    @Test
    void channelConditionGatesByChannel() {
        Promotion pass = fullReduction("p1", 20).condition(ChannelCondition.of("APP")).build();
        Promotion fail = fullReduction("p2", 20).condition(ChannelCondition.of("H5")).build();
        PriceResult result = new PriceEngine().quote(List.of(pass, fail), ctx());
        assertThat(result.applied()).hasSize(1);
        assertThat(result.applied().get(0).promotionId()).isEqualTo("p1");
        assertThat(result.rejectedReasons()).anyMatch(r -> r.contains("p2") && r.contains("渠道"));
    }

    @Test
    void memberLevelConditionGatesByLevel() {
        Promotion pass = fullReduction("p1", 20).condition(new MemberLevelCondition(1)).build();
        Promotion fail = fullReduction("p2", 20).condition(new MemberLevelCondition(3)).build();
        PriceResult result = new PriceEngine().quote(List.of(pass, fail), ctx());
        assertThat(result.applied()).hasSize(1);
        assertThat(result.applied().get(0).promotionId()).isEqualTo("p1");
        assertThat(result.rejectedReasons()).anyMatch(r -> r.contains("p2") && r.contains("会员等级"));
    }

    @Test
    void nonStackableGroupWinnerBeatsStackableSum() {
        Promotion a = fullReduction("p1", 40).stackable(false).exclusionGroup("base").build();
        Promotion b = fullReduction("p2", 20).stackable(false).exclusionGroup("base").build();
        Promotion c = fullReduction("p3", 25).build();
        PriceResult result = new PriceEngine().quote(List.of(a, b, c), ctx());
        // 互斥组胜出者 p1(40) 是非叠加独占，与可叠加 p3(25) 比：独占更大，整单只给 p1。
        assertThat(result.applied()).hasSize(1);
        assertThat(result.applied().get(0).promotionId()).isEqualTo("p1");
        assertThat(result.totalDiscount()).isEqualTo(Money.of("40.00", "CNY"));
    }

    @Test
    void tenantIsolationAcrossTwoTenants() {
        Promotion acme = fullReduction("p1", 20).tenantId("acme").build();
        Promotion globex = fullReduction("p2", 20).tenantId("globex").build();
        PriceResult result = new PriceEngine().quote(List.of(acme, globex), ctx());
        assertThat(result.applied()).hasSize(1);
        assertThat(result.applied().get(0).promotionId()).isEqualTo("p1");
        assertThat(result.rejectedReasons()).anyMatch(r -> r.contains("p2") && r.contains("不属于租户"));
    }

    @Test
    void discountRateStacksWithFullReduction() {
        Promotion r = rate("r", "0.90");
        Promotion f = fullReduction("f", 20).build();
        PriceResult result = new PriceEngine().quote(List.of(r, f), ctx());
        // 原价 120：折扣减 12.00 + 满减 20.00 = 32.00。
        assertThat(result.applied()).hasSize(2);
        assertThat(result.totalDiscount()).isEqualTo(Money.of("32.00", "CNY"));
        assertThat(result.payable()).isEqualTo(Money.of("88.00", "CNY"));
    }

    @Test
    void windowStartIsInclusive() {
        Promotion p = fullReduction("p1", 20).window(NOW, END).build();
        PriceResult result = new PriceEngine().quote(List.of(p), ctx());
        // 窗口起点等于当前时间：起点含入，活动可生效（与「当前时间早于起点 = 未开始」形成边界对照）。
        assertThat(result.applied()).hasSize(1);
        assertThat(result.applied().get(0).promotionId()).isEqualTo("p1");
    }

    @Test
    void windowEndBoundaryIsExclusive() {
        Promotion p = fullReduction("p1", 20).window(START, NOW).build();
        PriceResult result = new PriceEngine().quote(List.of(p), ctx());
        assertThat(result.applied()).isEmpty();
        assertThat(result.rejectedReasons()).anyMatch(r -> r.contains("p1") && r.contains("活动已结束"));
    }

    @Test
    void stackableSumCappedAtSubtotal() {
        Promotion a = fullReduction("p1", 100).build();
        Promotion b = fullReduction("p2", 50).build();
        PriceResult result = new PriceEngine().quote(List.of(a, b), ctx());
        // 原价 120，两券合计 150 超过原价：截断到 120，并留痕。
        assertThat(result.applied()).hasSize(2);
        assertThat(result.totalDiscount()).isEqualTo(Money.of("120.00", "CNY"));
        assertThat(result.rejectedReasons()).anyMatch(r -> r.contains("按原价截断"));
    }

    @Test
    void stackableTieBreaksByPriorityThenId() {
        Promotion a = fullReduction("a", 20).priority(5).build();
        Promotion b = fullReduction("b", 20).priority(1).build();
        PriceResult result = new PriceEngine().quote(List.of(a, b), ctx());
        // 优惠金额相等，priority 小者（b）排前。
        assertThat(result.applied()).extracting(Discount::promotionId).containsExactly("b", "a");
    }

    @Test
    void multipleStackableFollowBestFirstOrder() {
        List<Promotion> promotions = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            promotions.add(fullReduction("p" + i, i).build());
        }
        PriceResult result = new PriceEngine().quote(promotions, ctx());
        assertThat(result.applied())
                .extracting(Discount::promotionId)
                .containsExactly("p3", "p2", "p1");
        assertThat(result.totalDiscount()).isEqualTo(Money.of("6.00", "CNY"));
    }

    @Test
    void groupWinnerAndStackableReserveThenQuotaExhaustionAborts() {
        Promotion a = fullReduction("p1", 25).exclusionGroup("base").build();
        Promotion b = fullReduction("p2", 20).exclusionGroup("base").build();
        Promotion c = fullReduction("p3", 30).totalQuota(1).build();
        InMemoryQuotaStore store = new InMemoryQuotaStore();
        store.registerQuota(c);
        PriceEngine engine = new PriceEngine(store);
        PriceResult first = engine.quote(List.of(a, b, c), ctx());
        // 首单：互斥组胜出 p1(25) + 可叠加 p3(30) 都预留成功。
        assertThat(first.applied())
                .extracting(Discount::promotionId)
                .containsExactlyInAnyOrder("p1", "p3");
        assertThat(first.totalDiscount()).isEqualTo(Money.of("55.00", "CNY"));
        // 次单：p3 名额用尽，名额不足触发整单作废（原子回滚）。
        PriceResult second = engine.quote(List.of(a, b, c), ctx());
        assertThat(second.applied()).isEmpty();
        assertThat(second.rejectedReasons()).anyMatch(r -> r.contains("p3") && r.contains("名额不足"));
    }

    @Test
    void mixedExclusionConditionAndStackable() {
        Promotion a = fullReduction("p1", 30).exclusionGroup("base").build();
        Promotion b = fullReduction("p2", 20).exclusionGroup("base").build();
        Promotion c = fullReduction("p3", 10).condition(CategoryInCondition.of("food")).build();
        Promotion d = fullReduction("p4", 15).condition(CategoryInCondition.of("book")).build();
        PriceResult result = new PriceEngine().quote(List.of(a, b, c, d), ctx());
        // 互斥组留 p1(30)；c 类目不满足被砍；d 类目满足；p1 与 d 叠加。
        assertThat(result.applied())
                .extracting(Discount::promotionId)
                .containsExactlyInAnyOrder("p1", "p4");
        assertThat(result.totalDiscount()).isEqualTo(Money.of("45.00", "CNY"));
        assertThat(result.rejectedReasons()).anyMatch(r -> r.contains("p2") && r.contains("互斥组"));
        assertThat(result.rejectedReasons())
                .anyMatch(r -> r.contains("p3") && r.contains("指定类目 food"));
    }

    @Test
    void exclusionGroupMemberPaused() {
        Promotion a = fullReduction("p1", 30).exclusionGroup("base").build();
        Promotion b =
                fullReduction("p2", 20)
                        .exclusionGroup("base")
                        .status(PromotionStatus.PAUSED)
                        .build();
        PriceResult result = new PriceEngine().quote(List.of(a, b), ctx());
        assertThat(result.applied()).hasSize(1);
        assertThat(result.applied().get(0).promotionId()).isEqualTo("p1");
        assertThat(result.rejectedReasons())
                .anyMatch(r -> r.contains("p2") && r.contains("状态为 PAUSED"));
    }

    @Test
    void exclusionGroupMemberEnded() {
        Promotion a = fullReduction("p1", 30).exclusionGroup("base").build();
        Promotion b =
                fullReduction("p2", 20)
                        .exclusionGroup("base")
                        .window(START, NOW.minusSeconds(1))
                        .build();
        PriceResult result = new PriceEngine().quote(List.of(a, b), ctx());
        assertThat(result.applied()).hasSize(1);
        assertThat(result.applied().get(0).promotionId()).isEqualTo("p1");
        assertThat(result.rejectedReasons()).anyMatch(r -> r.contains("p2") && r.contains("活动已结束"));
    }

    @Test
    void fiveStackableCappedAtDepthThree() {
        List<Promotion> promotions = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            promotions.add(fullReduction("p" + i, i).build());
        }
        PriceResult result = new PriceEngine(null, 3).quote(promotions, ctx());
        // 深度 3：只保留优惠最大的 p5/p4/p3。
        assertThat(result.applied())
                .extracting(Discount::promotionId)
                .containsExactly("p5", "p4", "p3");
        assertThat(result.totalDiscount()).isEqualTo(Money.of("12.00", "CNY"));
    }

    @Test
    void threeStackablesBeatExclusive() {
        Promotion a = fullReduction("p1", 10).build();
        Promotion b = fullReduction("p2", 10).build();
        Promotion c = fullReduction("p3", 10).build();
        Promotion d = fullReduction("p4", 25).stackable(false).build();
        PriceResult result = new PriceEngine().quote(List.of(a, b, c, d), ctx());
        // 可叠加三项合计 30 > 独占 25，独占落败（不计入拒绝原因，只是没被选上）。
        assertThat(result.applied())
                .extracting(Discount::promotionId)
                .containsExactly("p1", "p2", "p3");
        assertThat(result.totalDiscount()).isEqualTo(Money.of("30.00", "CNY"));
    }

    @Test
    void threeStackablesWithMixedStrategies() {
        Promotion r = rate("r", "0.90");
        Promotion f1 = fullReduction("f1", 20).build();
        Promotion f2 = fullReduction("f2", 15).build();
        PriceResult result = new PriceEngine().quote(List.of(r, f1, f2), ctx());
        // 折扣 12.00 + 满减 20.00 + 满减 15.00 = 47.00。
        assertThat(result.applied()).hasSize(3);
        assertThat(result.totalDiscount()).isEqualTo(Money.of("47.00", "CNY"));
    }
}

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
import io.github.nephets.promokit.core.strategy.FullReductionStrategy;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromotionTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-02-01T00:00:00Z");

    private static Promotion.Builder base() {
        return Promotion.builder()
                .promotionId("p1")
                .tenantId("acme")
                .name("满100减20")
                .type(PromotionType.FULL_REDUCTION)
                .strategy(
                        new FullReductionStrategy(
                                Money.of("100.00", "CNY"), Money.of("20.00", "CNY")))
                .window(START, END);
    }

    @Test
    void defaultsAreActiveStackableAndPriorityZero() {
        Promotion promotion = base().build();
        assertThat(promotion.status()).isEqualTo(PromotionStatus.ACTIVE);
        assertThat(promotion.stackable()).isTrue();
        assertThat(promotion.priority()).isZero();
        assertThat(promotion.conditions()).isEmpty();
        assertThat(promotion.exclusionGroup()).isNull();
        assertThat(promotion.hasQuota()).isFalse();
        assertThat(promotion).hasToString("Promotion{p1, 满100减20, FULL_REDUCTION, ACTIVE}");
        Promotion withNulls =
                base().conditions(null).condition(null).totalQuota(null).perUserQuota(null).build();
        assertThat(withNulls.conditions()).isEmpty();
        assertThat(withNulls.totalQuota()).isNull();
        assertThat(withNulls.perUserQuota()).isNull();
    }

    @Test
    void requiredFieldsAreChecked() {
        assertThatThrownBy(() -> Promotion.builder().build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("promotionId");
        assertThatThrownBy(() -> base().tenantId(null).build())
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> base().name(null).build())
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> base().type(null).build())
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> base().strategy(null).build())
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> base().startTime(null).build())
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> base().status(null).build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void windowMustBeOrdered() {
        assertThatThrownBy(() -> base().window(END, START).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endTime");
    }

    @Test
    void quotaMustNotBeNegative() {
        assertThatThrownBy(() -> base().totalQuota(-1).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> base().perUserQuota(-1).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(base().totalQuota(10).build().hasQuota()).isTrue();
        assertThat(base().perUserQuota(1).build().hasQuota()).isTrue();
    }

    @Test
    void windowBoundariesAreHalfOpen() {
        Promotion promotion = base().build();
        assertThat(promotion.isWithinWindow(START.minusMillis(1))).isFalse();
        assertThat(promotion.isWithinWindow(START)).isTrue();
        assertThat(promotion.isWithinWindow(END.minusMillis(1))).isTrue();
        assertThat(promotion.isWithinWindow(END)).isFalse();
    }

    @Test
    void isActiveAtNeedsBothStatusAndWindow() {
        Promotion paused = base().status(PromotionStatus.PAUSED).window(START, END).build();
        assertThat(paused.isActiveAt(START.plusSeconds(1))).isFalse();
        assertThat(base().build().isActiveAt(START.plusSeconds(1))).isTrue();
        assertThat(base().status(PromotionStatus.ENDED).build().isActiveAt(START)).isFalse();
    }

    @Test
    void conditionsMatchWhenAllHit() {
        Promotion promotion =
                base().condition(new MinAmountCondition(Money.of("50.00", "CNY")))
                        .condition(new MinAmountCondition(Money.of("200.00", "CNY")))
                        .build();
        Cart cart =
                Cart.of(
                        "acme",
                        "u1",
                        0,
                        "APP",
                        List.of(new CartItem("sku-1", "book", Money.of("100.00", "CNY"), 1)));
        PromotionContext ctx = PromotionContext.builder(cart).build();
        assertThat(promotion.conditionsMatch(ctx)).isFalse();
        assertThat(promotion.firstUnmatchedCondition(ctx)).isPresent();
        assertThat(promotion.firstUnmatchedCondition(ctx).get().describe())
                .isEqualTo("订单满 200.00 CNY");
    }

    @Test
    void conditionListIsImmutable() {
        Promotion promotion =
                base().condition(new MinAmountCondition(Money.of("1.00", "CNY"))).build();
        List<Condition> external = new ArrayList<>(promotion.conditions());
        assertThat(external).hasSize(1);
        assertThatThrownBy(
                        () ->
                                promotion
                                        .conditions()
                                        .add(new MinAmountCondition(Money.of("2.00", "CNY"))))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

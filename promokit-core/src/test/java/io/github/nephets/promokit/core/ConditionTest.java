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
import io.github.nephets.promokit.core.condition.NewCustomerCondition;
import io.github.nephets.promokit.core.condition.OrCondition;
import io.github.nephets.promokit.core.condition.SkuInCondition;
import io.github.nephets.promokit.core.condition.TimeWindowCondition;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConditionTest {

    private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");

    private static PromotionContext ctx(Cart cart) {
        return PromotionContext.builder(cart).now(NOW).memberLevel(cart.memberLevel()).build();
    }

    private static Cart cart() {
        return Cart.of(
                "acme",
                "u1",
                2,
                "APP",
                List.of(
                        new CartItem("sku-1", "book", Money.of("60.00", "CNY"), 1),
                        new CartItem("sku-2", "toy", Money.of("40.00", "CNY"), 2)));
    }

    @Test
    void minAmountMatchesWhenSubtotalReachesThreshold() {
        assertThat(new MinAmountCondition(Money.of("100.00", "CNY")).matches(ctx(cart()))).isTrue();
        assertThat(new MinAmountCondition(Money.of("140.01", "CNY")).matches(ctx(cart())))
                .isFalse();
        assertThat(new MinAmountCondition(Money.of("100.00", "CNY")).describe())
                .isEqualTo("订单满 100.00 CNY");
        assertThat(new MinAmountCondition(Money.of("100.00", "CNY")).minAmount())
                .isEqualTo(Money.of("100.00", "CNY"));
    }

    @Test
    void skuInMatchesAnyItem() {
        assertThat(SkuInCondition.of("sku-2", "sku-9").matches(ctx(cart()))).isTrue();
        assertThat(SkuInCondition.of("sku-9").matches(ctx(cart()))).isFalse();
        assertThat(SkuInCondition.of("sku-1").describe()).isEqualTo("指定商品 sku-1");
        assertThat(SkuInCondition.of("sku-1").skuIds()).containsExactly("sku-1");
    }

    @Test
    void categoryInMatchesAnyItem() {
        assertThat(CategoryInCondition.of("toy").matches(ctx(cart()))).isTrue();
        assertThat(CategoryInCondition.of("food").matches(ctx(cart()))).isFalse();
        assertThat(CategoryInCondition.of("book", "toy").describe()).isEqualTo("指定类目 book,toy");
        assertThat(CategoryInCondition.of("book").categoryIds()).containsExactly("book");
    }

    @Test
    void memberLevelUsesGreaterOrEqual() {
        assertThat(new MemberLevelCondition(2).matches(ctx(cart()))).isTrue();
        assertThat(new MemberLevelCondition(3).matches(ctx(cart()))).isFalse();
        assertThat(new MemberLevelCondition(2).describe()).isEqualTo("会员等级 >= 2");
        assertThat(new MemberLevelCondition(2).minLevel()).isEqualTo(2);
    }

    @Test
    void newCustomerFlagIsComparedWithExpectation() {
        assertThat(new NewCustomerCondition().matches(ctx(cart()))).isFalse();
        assertThat(new NewCustomerCondition(false).matches(ctx(cart()))).isTrue();
        assertThat(new NewCustomerCondition().describe()).isEqualTo("仅限新客");
        assertThat(new NewCustomerCondition(false).describe()).isEqualTo("仅限老客");
        assertThat(new NewCustomerCondition(false).expected()).isFalse();
    }

    @Test
    void channelMustBeInSet() {
        assertThat(ChannelCondition.of("APP", "H5").matches(ctx(cart()))).isTrue();
        assertThat(ChannelCondition.of("H5").matches(ctx(cart()))).isFalse();
        assertThat(ChannelCondition.of("APP").describe()).isEqualTo("渠道属于 APP");
        assertThat(ChannelCondition.of("APP").channels()).containsExactly("APP");
    }

    @Test
    void timeWindowIsHalfOpen() {
        TimeWindowCondition condition =
                new TimeWindowCondition(NOW.minusSeconds(10), NOW.plusSeconds(10));
        assertThat(condition.matches(ctx(cart()))).isTrue();
        assertThat(
                        new TimeWindowCondition(NOW.plusSeconds(1), NOW.plusSeconds(10))
                                .matches(ctx(cart())))
                .isFalse();
        assertThat(new TimeWindowCondition(NOW.minusSeconds(10), NOW).matches(ctx(cart())))
                .isFalse();
        assertThat(condition.describe()).contains("2026-06-01");
        assertThat(condition.start()).isEqualTo(NOW.minusSeconds(10));
        assertThat(condition.end()).isEqualTo(NOW.plusSeconds(10));
    }

    @Test
    void andRequiresEveryCondition() {
        AndCondition all =
                AndCondition.of(
                        new MinAmountCondition(Money.of("10.00", "CNY")),
                        new MemberLevelCondition(1));
        AndCondition mixed =
                AndCondition.of(
                        new MinAmountCondition(Money.of("10.00", "CNY")),
                        new MemberLevelCondition(9));
        assertThat(all.matches(ctx(cart()))).isTrue();
        assertThat(mixed.matches(ctx(cart()))).isFalse();
        assertThat(all.describe()).isEqualTo("订单满 10.00 CNY 且 会员等级 >= 1");
        assertThat(all).hasToString("(订单满 10.00 CNY 且 会员等级 >= 1)");
        assertThat(all.conditions()).hasSize(2);
        assertThat(AndCondition.of().matches(ctx(cart()))).isTrue();
    }

    @Test
    void orNeedsOnlyOneCondition() {
        OrCondition any =
                OrCondition.of(
                        new MemberLevelCondition(9),
                        new MinAmountCondition(Money.of("10.00", "CNY")));
        assertThat(any.matches(ctx(cart()))).isTrue();
        assertThat(OrCondition.of(new MemberLevelCondition(9)).matches(ctx(cart()))).isFalse();
        assertThat(OrCondition.of().matches(ctx(cart()))).isFalse();
        assertThat(any.describe()).isEqualTo("会员等级 >= 9 或 订单满 10.00 CNY");
        assertThat(any).hasToString("(会员等级 >= 9 或 订单满 10.00 CNY)");
        assertThat(any.conditions()).hasSize(2);
    }

    @Test
    void defaultDescribeFallsBackToClassName() {
        Condition custom = ctx -> true;
        assertThat(custom.describe()).isNotBlank();
        assertThat(new CategoryInCondition(Set.of("book")).matches(ctx(cart()))).isTrue();
        assertThatThrownBy(() -> new SkuInCondition(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CategoryInCondition(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ChannelCondition(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SkuInCondition.of((String[]) null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TimeWindowCondition(null, NOW))
                .isInstanceOf(NullPointerException.class);
    }
}

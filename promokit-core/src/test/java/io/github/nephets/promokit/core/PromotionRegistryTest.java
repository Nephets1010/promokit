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

import io.github.nephets.promokit.core.strategy.FullReductionStrategy;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromotionRegistryTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-12-31T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");

    private static Promotion promotion(String tenantId, String id, int priority) {
        return Promotion.builder()
                .promotionId(id)
                .tenantId(tenantId)
                .name(id)
                .type(PromotionType.FULL_REDUCTION)
                .strategy(
                        new FullReductionStrategy(
                                Money.of("1.00", "CNY"), Money.of("10.00", "CNY")))
                .window(START, END)
                .priority(priority)
                .build();
    }

    @Test
    void registerAndFind() {
        PromotionRegistry registry = new PromotionRegistry();
        registry.register(promotion("acme", "p1", 0));
        assertThat(registry.find("acme", "p1")).isPresent();
        assertThat(registry.find("acme", "missing")).isEmpty();
        assertThat(registry.find("globex", "p1")).isEmpty();
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void registeringSameIdOverwrites() {
        PromotionRegistry registry = new PromotionRegistry();
        registry.register(promotion("acme", "p1", 0));
        registry.register(promotion("acme", "p1", 5));
        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.find("acme", "p1").get().priority()).isEqualTo(5);
    }

    @Test
    void unregisterRemovesPromotion() {
        PromotionRegistry registry = new PromotionRegistry();
        registry.register(promotion("acme", "p1", 0));
        registry.unregister("acme", "p1");
        registry.unregister("globex", "p1");
        assertThat(registry.size()).isZero();
    }

    @Test
    void byTenantIsSortedByPriorityThenId() {
        PromotionRegistry registry = new PromotionRegistry();
        registry.register(promotion("acme", "b", 1));
        registry.register(promotion("acme", "a", 5));
        registry.register(promotion("acme", "c", 1));
        assertThat(registry.byTenant("acme"))
                .extracting(Promotion::promotionId)
                .containsExactly("b", "c", "a");
        assertThat(registry.byTenant("globex")).isEmpty();
    }

    @Test
    void activeFiltersByStatusAndWindow() {
        PromotionRegistry registry = new PromotionRegistry();
        registry.register(promotion("acme", "p1", 0));
        registry.register(
                Promotion.builder()
                        .promotionId("p2")
                        .tenantId("acme")
                        .name("p2")
                        .type(PromotionType.FULL_REDUCTION)
                        .strategy(
                                new FullReductionStrategy(
                                        Money.of("1.00", "CNY"), Money.of("10.00", "CNY")))
                        .window(START, END)
                        .status(PromotionStatus.PAUSED)
                        .build());
        registry.register(
                Promotion.builder()
                        .promotionId("p3")
                        .tenantId("acme")
                        .name("p3")
                        .type(PromotionType.FULL_REDUCTION)
                        .strategy(
                                new FullReductionStrategy(
                                        Money.of("1.00", "CNY"), Money.of("10.00", "CNY")))
                        .window(NOW.plusSeconds(60), END)
                        .build());
        assertThat(registry.active("acme", NOW))
                .extracting(Promotion::promotionId)
                .containsExactly("p1");
    }

    @Test
    void tenantIdsAndClear() {
        PromotionRegistry registry = new PromotionRegistry();
        registry.register(promotion("acme", "p1", 0));
        registry.register(promotion("globex", "p1", 0));
        assertThat(registry.tenantIds()).containsExactlyInAnyOrder("acme", "globex");
        registry.clear();
        assertThat(registry.size()).isZero();
        assertThat(registry.tenantIds()).isEmpty();
    }

    @Test
    void unregisterOfUnknownTenantOrPromotionIsNoOp() {
        PromotionRegistry registry = new PromotionRegistry();
        registry.register(promotion("acme", "p1", 0));
        registry.unregister("globex", "p1");
        registry.unregister("acme", "missing");
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void findMissingPromotionIsEmpty() {
        PromotionRegistry registry = new PromotionRegistry();
        registry.register(promotion("acme", "p1", 0));
        assertThat(registry.find("acme", "p1")).isPresent();
        assertThat(registry.find("acme", "missing")).isEmpty();
    }

    @Test
    void rejectsBlankKeys() {
        PromotionRegistry registry = new PromotionRegistry();
        assertThatThrownBy(() -> registry.register(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> registry.unregister("acme", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.unregister(" ", "p1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.find(null, "p1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.find("acme", ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.byTenant(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.active("acme", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void capacityConstructorRejectsNonPositive() {
        assertThatThrownBy(() -> new PromotionRegistry(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PromotionRegistry(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new PromotionRegistry().capacity())
                .isEqualTo(PromotionRegistry.DEFAULT_CAPACITY);
    }

    @Test
    void evictsOldestPromotionWhenOverCapacity() {
        int capacity = 2;
        PromotionRegistry registry = new PromotionRegistry(capacity);
        registry.register(promotion("acme", "p1", 0));
        registry.register(promotion("acme", "p2", 0));
        assertThat(registry.size()).isEqualTo(2);
        // 第 3 条触发淘汰：p1 最久没被访问，被挤出去；p2、p3 仍在。
        registry.register(promotion("acme", "p3", 0));
        assertThat(registry.size()).isEqualTo(capacity);
        assertThat(registry.find("acme", "p1")).isEmpty();
        assertThat(registry.find("acme", "p2")).isPresent();
        assertThat(registry.find("acme", "p3")).isPresent();
    }

    @Test
    void recentLookupKeepsHotPromotion() {
        int capacity = 2;
        PromotionRegistry registry = new PromotionRegistry(capacity);
        registry.register(promotion("acme", "p1", 0));
        registry.register(promotion("acme", "p2", 0));
        // 访问 p1 把它刷成最近使用，于是第 3 条挤走的是 p2。
        assertThat(registry.find("acme", "p1")).isPresent();
        registry.register(promotion("acme", "p3", 0));
        assertThat(registry.find("acme", "p1")).isPresent();
        assertThat(registry.find("acme", "p2")).isEmpty();
    }

    @Test
    void evictionDropsEmptyTenantBucket() {
        int capacity = 1;
        PromotionRegistry registry = new PromotionRegistry(capacity);
        registry.register(promotion("acme", "p1", 0));
        registry.register(promotion("globex", "p1", 0));
        assertThat(registry.tenantIds()).containsExactly("globex");
        assertThat(registry.size()).isEqualTo(1);
    }
}

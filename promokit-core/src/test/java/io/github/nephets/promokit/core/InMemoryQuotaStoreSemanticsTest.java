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

/** 配额双维度与幂等语义：per-user 与 total 独立生效、confirm/release 幂等、淘汰后的降级语义。 */
class InMemoryQuotaStoreSemanticsTest {

    private static Promotion promotion(String id, Integer total, Integer perUser) {
        return Promotion.builder()
                .promotionId(id)
                .tenantId("acme")
                .name("活动")
                .type(PromotionType.FULL_REDUCTION)
                .strategy(
                        new FullReductionStrategy(Money.of("1.00", "CNY"), Money.of("1.00", "CNY")))
                .window(
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-12-31T00:00:00Z"))
                .totalQuota(total)
                .perUserQuota(perUser)
                .build();
    }

    @Test
    void totalAndPerUserLimitsAreEnforcedIndependently() {
        InMemoryQuotaStore store = new InMemoryQuotaStore();
        store.registerQuota("acme", "p1", 5, 2);
        for (int i = 0; i < 2; i++) {
            assertThat(store.reserve("acme", "p1", "u1")).isTrue();
        }
        // 每人 2 次用完：同一用户第三次被拒。
        assertThat(store.reserve("acme", "p1", "u1")).isFalse();
        // 换用户还能用，总名额 5 还没满。
        assertThat(store.reserve("acme", "p1", "u2")).isTrue();
        assertThat(store.reserve("acme", "p1", "u3")).isTrue();
        assertThat(store.reserve("acme", "p1", "u4")).isTrue();
        assertThat(store.usedTotal("acme", "p1")).isEqualTo(5);
        assertThat(store.usedByUser("acme", "p1", "u1")).isEqualTo(2);
    }

    @Test
    void usedCountersIncludeReservedAndConfirmed() {
        InMemoryQuotaStore store = new InMemoryQuotaStore();
        store.registerQuota("acme", "p1", 10, 10);
        store.reserve("acme", "p1", "u1");
        store.reserve("acme", "p1", "u1");
        store.confirm("acme", "p1", "u1");
        assertThat(store.usedByUser("acme", "p1", "u1")).isEqualTo(2);
        assertThat(store.usedTotal("acme", "p1")).isEqualTo(2);
        assertThat(store.confirmedTotal("acme", "p1")).isEqualTo(1);
    }

    @Test
    void confirmIsIdempotent() {
        InMemoryQuotaStore store = new InMemoryQuotaStore();
        store.registerQuota("acme", "p1", 10, 10);
        store.reserve("acme", "p1", "u1");
        store.confirm("acme", "p1", "u1");
        store.confirm("acme", "p1", "u1");
        // 第二次 confirm 没有预留可转，用量不重复累加。
        assertThat(store.confirmedTotal("acme", "p1")).isEqualTo(1);
        assertThat(store.usedTotal("acme", "p1")).isEqualTo(1);
    }

    @Test
    void releasedQuotaCanBeReservedAgain() {
        InMemoryQuotaStore store = new InMemoryQuotaStore();
        store.registerQuota("acme", "p1", 1, 1);
        assertThat(store.reserve("acme", "p1", "u1")).isTrue();
        assertThat(store.reserve("acme", "p1", "u2")).isFalse();
        store.release("acme", "p1", "u1");
        assertThat(store.usedTotal("acme", "p1")).isZero();
        assertThat(store.reserve("acme", "p1", "u2")).isTrue();
    }

    @Test
    void evictedEntryDegradesToUnlimited() {
        InMemoryQuotaStore store = new InMemoryQuotaStore(1);
        store.registerQuota("acme", "p1", 1, null);
        store.reserve("acme", "p1", "u1");
        // 第二个 key 把 p1 挤出去：限额丢失，之后按不限量放行（有 WARN 留痕）。
        store.registerQuota("acme", "p2", null, 1);
        assertThat(store.reserve("acme", "p1", "u2")).isTrue();
        assertThat(store.reserve("acme", "p1", "u3")).isTrue();
        assertThat(store.usedTotal("acme", "p1")).isEqualTo(3);
    }

    @Test
    void registerWithBothQuotasNullRegistersNothing() {
        InMemoryQuotaStore store = new InMemoryQuotaStore();
        store.registerQuota("acme", "p1", null, null);
        assertThat(store.reserve("acme", "p1", "u1")).isTrue();
        assertThat(store.reserve("acme", "p1", "u2")).isTrue();
    }

    @Test
    void perUserOnlyRegistrationBlocksRepeatUsers() {
        InMemoryQuotaStore store = new InMemoryQuotaStore();
        store.registerQuota(promotion("p1", null, 1));
        assertThat(store.reserve("acme", "p1", "u1")).isTrue();
        assertThat(store.reserve("acme", "p1", "u1")).isFalse();
        assertThat(store.reserve("acme", "p1", "u2")).isTrue();
    }

    @Test
    void clearThenReserveTreatsEverythingAsUnlimited() {
        InMemoryQuotaStore store = new InMemoryQuotaStore();
        store.registerQuota("acme", "p1", 1, 1);
        store.reserve("acme", "p1", "u1");
        store.clear();
        assertThat(store.usedTotal("acme", "p1")).isZero();
        assertThat(store.reserve("acme", "p1", "u1")).isTrue();
    }
}

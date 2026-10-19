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

class InMemoryQuotaStoreTest {

    private static Promotion promotion(Integer totalQuota, Integer perUserQuota) {
        return Promotion.builder()
                .promotionId("p1")
                .tenantId("acme")
                .name("限量活动")
                .type(PromotionType.FULL_REDUCTION)
                .strategy(
                        new FullReductionStrategy(
                                Money.of("1.00", "CNY"), Money.of("10.00", "CNY")))
                .window(
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2027-01-01T00:00:00Z"))
                .totalQuota(totalQuota)
                .perUserQuota(perUserQuota)
                .build();
    }

    @Test
    void reserveIncrementsUsage() {
        InMemoryQuotaStore store = new InMemoryQuotaStore();
        store.registerQuota("acme", "p1", 5, 2);
        assertThat(store.reserve("acme", "p1", "u1")).isTrue();
        assertThat(store.usedTotal("acme", "p1")).isEqualTo(1);
        assertThat(store.usedByUser("acme", "p1", "u1")).isEqualTo(1);
        assertThat(store.confirmedTotal("acme", "p1")).isZero();
    }

    @Test
    void totalQuotaStopsFurtherReservations() {
        InMemoryQuotaStore store = new InMemoryQuotaStore();
        store.registerQuota("acme", "p1", 2, null);
        assertThat(store.reserve("acme", "p1", "u1")).isTrue();
        assertThat(store.reserve("acme", "p1", "u2")).isTrue();
        assertThat(store.reserve("acme", "p1", "u3")).isFalse();
        assertThat(store.usedTotal("acme", "p1")).isEqualTo(2);
    }

    @Test
    void perUserQuotaAppliesPerUser() {
        InMemoryQuotaStore store = new InMemoryQuotaStore();
        store.registerQuota("acme", "p1", null, 1);
        assertThat(store.reserve("acme", "p1", "u1")).isTrue();
        assertThat(store.reserve("acme", "p1", "u1")).isFalse();
        assertThat(store.reserve("acme", "p1", "u2")).isTrue();
    }

    @Test
    void confirmMovesReservedToConfirmed() {
        InMemoryQuotaStore store = new InMemoryQuotaStore();
        store.registerQuota("acme", "p1", 5, 5);
        store.reserve("acme", "p1", "u1");
        store.confirm("acme", "p1", "u1");
        assertThat(store.confirmedTotal("acme", "p1")).isEqualTo(1);
        assertThat(store.usedTotal("acme", "p1")).isEqualTo(1);
        store.confirm("acme", "p1", "u1");
        assertThat(store.confirmedTotal("acme", "p1")).isEqualTo(1);
    }

    @Test
    void confirmWithoutReservationIsNoOp() {
        InMemoryQuotaStore store = new InMemoryQuotaStore();
        store.confirm("acme", "p1", "u1");
        assertThat(store.usedTotal("acme", "p1")).isZero();
        assertThat(store.usedByUser("acme", "p1", "u1")).isZero();
    }

    @Test
    void releaseReturnsTheQuota() {
        InMemoryQuotaStore store = new InMemoryQuotaStore();
        store.registerQuota("acme", "p1", 1, 1);
        assertThat(store.reserve("acme", "p1", "u1")).isTrue();
        assertThat(store.reserve("acme", "p1", "u2")).isFalse();
        store.release("acme", "p1", "u1");
        assertThat(store.reserve("acme", "p1", "u2")).isTrue();
    }

    @Test
    void releaseIsIdempotent() {
        InMemoryQuotaStore store = new InMemoryQuotaStore();
        store.release("acme", "p1", "u1");
        store.release("acme", "p1", "u1");
        assertThat(store.usedTotal("acme", "p1")).isZero();
    }

    @Test
    void unregisteredPromotionIsUnlimited() {
        InMemoryQuotaStore store = new InMemoryQuotaStore();
        for (int i = 0; i < 10; i++) {
            assertThat(store.reserve("acme", "unknown", "u1")).isTrue();
        }
        assertThat(store.usedTotal("acme", "unknown")).isEqualTo(10);
    }

    @Test
    void registerQuotaFromPromotion() {
        InMemoryQuotaStore store = new InMemoryQuotaStore();
        store.registerQuota(promotion(1, 1));
        assertThat(store.reserve("acme", "p1", "u1")).isTrue();
        assertThat(store.reserve("acme", "p1", "u2")).isFalse();
        assertThatThrownBy(() -> store.registerQuota(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.registerQuota("acme", null, 1, 1))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void clearResetsEverything() {
        InMemoryQuotaStore store = new InMemoryQuotaStore();
        store.registerQuota(promotion(1, 1));
        store.reserve("acme", "p1", "u1");
        store.clear();
        assertThat(store.usedTotal("acme", "p1")).isZero();
        assertThat(store.reserve("acme", "p1", "u1")).isTrue();
    }

    @Test
    void evictsOldestEntryWhenOverCapacity() {
        int capacity = 3;
        InMemoryQuotaStore store = new InMemoryQuotaStore(capacity);
        for (int i = 0; i < capacity; i++) {
            store.registerQuota("acme", "p" + i, 1, null);
        }
        // 已占满 3 条逻辑条目。再登记一条会触发淘汰最旧的那条（"p0"）。
        store.registerQuota("acme", "p3", 1, null);
        // 最新的条目（"p3"）仍在容量内，保留限额语义。
        assertThat(store.reserve("acme", "p3", "u1")).isTrue();
        assertThat(store.reserve("acme", "p3", "u2")).isFalse();
        // 最旧的那条（"p0"）已被淘汰，限额移除后视为不限量。
        assertThat(store.reserve("acme", "p0", "u1")).isTrue();
        assertThat(store.reserve("acme", "p0", "u2")).isTrue();
    }

    @Test
    void capacityConstructorRejectsNonPositive() {
        assertThatThrownBy(() -> new InMemoryQuotaStore(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InMemoryQuotaStore(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullIdentifiers() {
        InMemoryQuotaStore store = new InMemoryQuotaStore();
        assertThatThrownBy(() -> store.registerQuota("acme", null, 1, 1))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.reserve("acme", "p1", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.reserve("acme", null, "u1"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.reserve(null, "p1", "u1"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.confirm("acme", "p1", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.release("acme", null, "u1"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.usedByUser("acme", "p1", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.usedTotal(null, "p1"))
                .isInstanceOf(NullPointerException.class);
    }
}

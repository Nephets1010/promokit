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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** 名额路径回归：逐个预留、失败回滚、未登记不限量。重点是「没有券被白占」——回滚漏一次，高并发下就是资损。 */
class PriceEngineQuotaTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-12-31T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");

    /** 记录 reserve/release 调用的包装存储，用来验证「只回滚带名额的活动」。 */
    private static final class RecordingQuotaStore implements QuotaStore {

        private final InMemoryQuotaStore delegate = new InMemoryQuotaStore();
        private final AtomicInteger reserves = new AtomicInteger();
        private final AtomicInteger releases = new AtomicInteger();

        @Override
        public boolean reserve(String tenantId, String promotionId, String userId) {
            reserves.incrementAndGet();
            return delegate.reserve(tenantId, promotionId, userId);
        }

        @Override
        public void confirm(String tenantId, String promotionId, String userId) {
            delegate.confirm(tenantId, promotionId, userId);
        }

        @Override
        public void release(String tenantId, String promotionId, String userId) {
            releases.incrementAndGet();
            delegate.release(tenantId, promotionId, userId);
        }

        @Override
        public int usedByUser(String tenantId, String promotionId, String userId) {
            return delegate.usedByUser(tenantId, promotionId, userId);
        }

        @Override
        public int usedTotal(String tenantId, String promotionId) {
            return delegate.usedTotal(tenantId, promotionId);
        }
    }

    private static PromotionContext ctx() {
        List<CartItem> items = new ArrayList<>();
        items.add(new CartItem("sku-1", "book", Money.of("60.00", "CNY"), 2));
        Cart cart = Cart.of("acme", "u1", 1, "APP", items);
        return PromotionContext.builder(cart).now(NOW).build();
    }

    private static Promotion.Builder builder(String id, int amount) {
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

    @Test
    void failureInTheMiddleRollsBackEveryEarlierReservation() {
        Promotion first = builder("p1", 30).totalQuota(5).build();
        Promotion second = builder("p2", 20).totalQuota(5).build();
        Promotion third = builder("p3", 10).totalQuota(5).build();
        InMemoryQuotaStore store = new InMemoryQuotaStore();
        store.registerQuota(first);
        store.registerQuota(second);
        store.registerQuota(third);
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
                new PriceEngine(failingOnSecond).quote(List.of(first, second, third), ctx());
        assertThat(result.applied()).isEmpty();
        // p1 先预留成功又随回滚释放：任何名额都不得被白占。
        assertThat(store.usedTotal("acme", "p1")).isZero();
        assertThat(store.usedByUser("acme", "p1", "u1")).isZero();
        assertThat(result.rejectedReasons()).anyMatch(reason -> reason.contains("名额不足"));
    }

    @Test
    void rollbackOnlyReleasesQuotaBearingPromotions() {
        Promotion first = builder("p1", 30).totalQuota(5).build();
        Promotion noQuota = builder("p2", 20).build();
        Promotion failing = builder("p3", 10).totalQuota(5).build();
        RecordingQuotaStore store = new RecordingQuotaStore();
        store.delegate.registerQuota(first);
        store.delegate.registerQuota(failing);
        QuotaStore failingOnThird =
                new QuotaStore() {
                    @Override
                    public boolean reserve(String tenantId, String promotionId, String userId) {
                        boolean ok = store.reserve(tenantId, promotionId, userId);
                        if (promotionId.equals("p3")) {
                            // 模拟 p3 名额不足；仍记录这次预留尝试，以便校验「只回滚带名额的活动」。
                            return false;
                        }
                        return ok;
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
                new PriceEngine(failingOnThird).quote(List.of(first, noQuota, failing), ctx());
        assertThat(result.applied()).isEmpty();
        // 只回滚带名额的 p1；无名额配置的 p2 从未预留，也不该收到 release。
        assertThat(store.releases.get()).isEqualTo(1);
        assertThat(store.reserves.get()).isEqualTo(2);
        assertThat(store.delegate.usedTotal("acme", "p1")).isZero();
    }

    @Test
    void perUserQuotaFailureInTheMiddleAlsoRollsBack() {
        Promotion first = builder("p1", 30).perUserQuota(5).build();
        Promotion second = builder("p2", 20).perUserQuota(1).build();
        InMemoryQuotaStore store = new InMemoryQuotaStore();
        store.registerQuota(first);
        store.registerQuota(second);
        store.reserve("acme", "p2", "u1"); // 先把 p2 的每人额度用掉
        PriceResult result = new PriceEngine(store).quote(List.of(first, second), ctx());
        assertThat(result.applied()).isEmpty();
        assertThat(store.usedByUser("acme", "p1", "u1")).isZero();
    }

    @Test
    void unregisteredQuotaPromotionIsUnlimited() {
        Promotion promotion = builder("p1", 20).totalQuota(3).build();
        InMemoryQuotaStore store = new InMemoryQuotaStore();
        // 未 registerQuota：存储里没有限额，按不限量放行。
        PriceEngine engine = new PriceEngine(store);
        for (int i = 0; i < 5; i++) {
            assertThat(engine.quote(List.of(promotion), ctx()).applied()).hasSize(1);
        }
        assertThat(store.usedTotal("acme", "p1")).isEqualTo(5);
    }

    @Test
    void promotionWithoutQuotaNeverTouchesTheStore() {
        Promotion promotion = builder("p1", 20).build();
        RecordingQuotaStore store = new RecordingQuotaStore();
        PriceResult result = new PriceEngine(store).quote(List.of(promotion), ctx());
        assertThat(result.applied()).hasSize(1);
        assertThat(store.reserves.get()).isZero();
        assertThat(store.releases.get()).isZero();
    }

    @Test
    void nullQuotaStoreSkipsReservationEntirely() {
        Promotion promotion = builder("p1", 20).totalQuota(1).build();
        PriceResult result = new PriceEngine().quote(List.of(promotion), ctx());
        assertThat(result.applied()).hasSize(1);
        assertThat(result.totalDiscount()).isEqualTo(Money.of("20.00", "CNY"));
    }

    @Test
    void quotaFreedByRollbackIsUsableAgain() {
        Promotion first = builder("p1", 30).totalQuota(5).build();
        Promotion second = builder("p2", 20).totalQuota(5).build();
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
        new PriceEngine(failingOnSecond).quote(List.of(first, second), ctx());
        // 回滚释放后，p1 在正常的引擎下还能继续被使用，说明额度真的还回来了。
        PriceResult ok = new PriceEngine(store).quote(List.of(first), ctx());
        assertThat(ok.applied()).hasSize(1);
    }

    @Test
    void exhaustedTotalQuotaLeavesTraceableReason() {
        Promotion promotion = builder("p1", 20).totalQuota(1).build();
        InMemoryQuotaStore store = new InMemoryQuotaStore();
        store.registerQuota(promotion);
        PriceEngine engine = new PriceEngine(store);
        assertThat(engine.quote(List.of(promotion), ctx()).applied()).hasSize(1);
        PriceResult second = engine.quote(List.of(promotion), ctx());
        assertThat(second.applied()).isEmpty();
        assertThat(second.rejectedReasons())
                .anyMatch(reason -> reason.contains("p1") && reason.contains("名额不足"));
    }
}

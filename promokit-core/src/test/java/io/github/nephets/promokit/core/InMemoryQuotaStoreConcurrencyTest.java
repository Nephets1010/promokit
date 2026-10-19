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

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** 并发抢占回归：名额窗口内并发预留，成功数必须恰好等于限额，不能超支。 */
class InMemoryQuotaStoreConcurrencyTest {

    @Test
    void concurrentReservationsNeverExceedTotalQuota() throws Exception {
        InMemoryQuotaStore store = new InMemoryQuotaStore();
        int quota = 10;
        store.registerQuota("acme", "p1", quota, null);
        int threads = 32;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(
                    () -> {
                        await(start);
                        if (store.reserve("acme", "p1", "u-" + Thread.currentThread().threadId())) {
                            successes.incrementAndGet();
                        }
                    });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        assertThat(successes.get()).isEqualTo(quota);
        assertThat(store.usedTotal("acme", "p1")).isEqualTo(quota);
    }

    @Test
    void concurrentReservationsNeverExceedPerUserQuota() throws Exception {
        InMemoryQuotaStore store = new InMemoryQuotaStore();
        int quota = 4;
        store.registerQuota("acme", "p1", null, quota);
        int threads = 32;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(
                    () -> {
                        await(start);
                        if (store.reserve("acme", "p1", "u1")) {
                            successes.incrementAndGet();
                        }
                    });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        assertThat(successes.get()).isEqualTo(quota);
        assertThat(store.usedByUser("acme", "p1", "u1")).isEqualTo(quota);
    }

    @Test
    void concurrentReserveConfirmReleaseKeepsCountersConsistent() throws Exception {
        InMemoryQuotaStore store = new InMemoryQuotaStore();
        store.registerQuota("acme", "p1", 1000, 1000);
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < threads; i++) {
            String user = "u" + i;
            pool.submit(
                    () -> {
                        await(start);
                        for (int round = 0; round < 50; round++) {
                            if (store.reserve("acme", "p1", user)) {
                                if (round % 2 == 0) {
                                    store.confirm("acme", "p1", user);
                                } else {
                                    store.release("acme", "p1", user);
                                }
                            }
                        }
                    });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        // 一半轮次 confirm、一半 release：最终用量 = 确认数，且永远不为负。
        assertThat(store.usedTotal("acme", "p1")).isEqualTo(store.confirmedTotal("acme", "p1"));
        assertThat(store.usedTotal("acme", "p1")).isGreaterThan(0);
        for (int i = 0; i < threads; i++) {
            assertThat(store.usedByUser("acme", "p1", "u" + i)).isNotNegative();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

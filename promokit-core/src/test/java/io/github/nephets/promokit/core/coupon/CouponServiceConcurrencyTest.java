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
package io.github.nephets.promokit.core.coupon;

import io.github.nephets.promokit.core.Cart;
import io.github.nephets.promokit.core.CartItem;
import io.github.nephets.promokit.core.CouponAlreadyUsedException;
import io.github.nephets.promokit.core.Money;
import io.github.nephets.promokit.core.PromotionContext;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 并发核销回归测试：用 ConcurrentHashMap.compute 原子化后，同一券码并发核销只成功一次， 避免重复折扣（资损）。修复前（非原子状态切换）会有多个线程都「成功」核销。
 */
class CouponServiceConcurrencyTest {

    private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private CouponService service() {
        CouponService s = new CouponService(CLOCK);
        s.registerTemplate(
                new CouponTemplate(
                        "t1",
                        "acme",
                        "券",
                        Money.of("15.00", "CNY"),
                        null,
                        NOW.minusSeconds(3600),
                        NOW.plusSeconds(3600),
                        5));
        return s;
    }

    private PromotionContext ctx() {
        Cart cart =
                Cart.of(
                        "acme",
                        "u1",
                        0,
                        "APP",
                        List.of(new CartItem("sku-1", "book", Money.of("10.00", "CNY"), 1)));
        return PromotionContext.builder(cart).now(NOW).build();
    }

    @Test
    void concurrentRedeemRedeemsExactlyOnce() throws Exception {
        CouponService s = service();
        String code = s.issue("t1", "u1").code();
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(
                    () -> {
                        try {
                            start.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        try {
                            s.redeem(code, ctx());
                            successes.incrementAndGet();
                        } catch (CouponAlreadyUsedException ignored) {
                            // 其余并发核销应被拒绝
                        }
                    });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        assertThat(successes.get()).isEqualTo(1);
        assertThat(s.find(code).get().status()).isEqualTo(CouponStatus.USED);
    }

    @Test
    void concurrentRedeemIfAbsentAllReturnUsed() throws Exception {
        CouponService s = service();
        String code = s.issue("t1", "u1").code();
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger used = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(
                    () -> {
                        try {
                            start.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        s.redeemIfAbsent(code, ctx())
                                .ifPresent(
                                        c -> {
                                            if (c.status() == CouponStatus.USED) {
                                                used.incrementAndGet();
                                            }
                                        });
                    });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        // 幂等核销：每个调用都返回已核销的券，不抛异常，也不重复折扣。
        assertThat(used.get()).isEqualTo(threads);
        assertThat(s.find(code).get().status()).isEqualTo(CouponStatus.USED);
    }
}

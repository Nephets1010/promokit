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
import io.github.nephets.promokit.core.CouponNotFoundException;
import io.github.nephets.promokit.core.Money;
import io.github.nephets.promokit.core.PromotionContext;
import io.github.nephets.promokit.core.PromotionExpiredException;
import io.github.nephets.promokit.core.PromotionNotApplicableException;
import io.github.nephets.promokit.core.QuotaExhaustedException;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static CouponTemplate template(String id, int totalQuantity, Money threshold) {
        return new CouponTemplate(
                id,
                "acme",
                "新人券",
                Money.of("15.00", "CNY"),
                threshold,
                NOW.minusSeconds(3600),
                NOW.plusSeconds(3600),
                totalQuantity);
    }

    private static PromotionContext ctx(String tenantId, Money amount) {
        Cart cart =
                Cart.of(
                        tenantId,
                        "u1",
                        0,
                        "APP",
                        List.of(new CartItem("sku-1", "book", amount, 1)));
        return PromotionContext.builder(cart).now(NOW).build();
    }

    @Test
    void issueCreatesUnusedCoupon() {
        CouponService service = new CouponService(CLOCK);
        service.registerTemplate(template("t1", 3, Money.of("100.00", "CNY")));
        Coupon coupon = service.issue("t1", "u1");
        assertThat(coupon.status()).isEqualTo(CouponStatus.UNUSED);
        assertThat(coupon.ownerId()).isEqualTo("u1");
        assertThat(coupon.tenantId()).isEqualTo("acme");
        assertThat(coupon.faceValue()).isEqualTo(Money.of("15.00", "CNY"));
        assertThat(coupon.code()).startsWith("CPN-t1-");
        assertThat(service.issuedCount("t1")).isEqualTo(1);
        assertThat(service.remaining("t1")).isEqualTo(2);
        assertThat(service.template("t1")).isPresent();
        assertThat(service.now()).isEqualTo(NOW);
    }

    @Test
    void issueStopsAtTotalQuantity() {
        CouponService service = new CouponService(CLOCK);
        service.registerTemplate(template("t1", 1, null));
        service.issue("t1", "u1");
        assertThatThrownBy(() -> service.issue("t1", "u2"))
                .isInstanceOf(QuotaExhaustedException.class)
                .hasMessageContaining("已发完");
        assertThat(service.issuedCount("t1")).isEqualTo(1);
    }

    @Test
    void issueRejectsUnknownTemplate() {
        CouponService service = new CouponService(CLOCK);
        assertThatThrownBy(() -> service.issue("missing", "u1"))
                .isInstanceOf(CouponNotFoundException.class);
        assertThat(service.issuedCount("missing")).isZero();
        assertThatThrownBy(() -> service.registerTemplate(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CouponService(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.remaining("missing"))
                .isInstanceOf(CouponNotFoundException.class);
    }

    @Test
    void redeemMarksCouponUsed() {
        CouponService service = new CouponService(CLOCK);
        service.registerTemplate(template("t1", 5, Money.of("100.00", "CNY")));
        Coupon coupon = service.issue("t1", "u1");
        Coupon used = service.redeem(coupon.code(), ctx("acme", Money.of("150.00", "CNY")));
        assertThat(used.status()).isEqualTo(CouponStatus.USED);
        assertThat(service.isRedeemed(coupon.code())).isTrue();
        assertThat(service.find(coupon.code())).isPresent();
        assertThat(service.couponsOf("u1")).hasSize(1);
        assertThat(service.couponsOf("nobody")).isEmpty();
        assertThat(service.isRedeemed("CPN-nope")).isFalse();
    }

    @Test
    void redeemTwiceThrows() {
        CouponService service = new CouponService(CLOCK);
        service.registerTemplate(template("t1", 5, null));
        Coupon coupon = service.issue("t1", "u1");
        service.redeem(coupon.code(), ctx("acme", Money.of("10.00", "CNY")));
        assertThatThrownBy(
                        () -> service.redeem(coupon.code(), ctx("acme", Money.of("10.00", "CNY"))))
                .isInstanceOf(CouponAlreadyUsedException.class);
    }

    @Test
    void redeemIfAbsentIsIdempotent() {
        CouponService service = new CouponService(CLOCK);
        service.registerTemplate(template("t1", 5, null));
        Coupon coupon = service.issue("t1", "u1");
        PromotionContext context = ctx("acme", Money.of("10.00", "CNY"));
        assertThat(service.redeemIfAbsent(coupon.code(), context).get().status())
                .isEqualTo(CouponStatus.USED);
        assertThat(service.redeemIfAbsent(coupon.code(), context).get().status())
                .isEqualTo(CouponStatus.USED);
        assertThat(service.issuedCount("t1")).isEqualTo(1);
        assertThat(service.redeemIfAbsent("CPN-nope", context)).isEmpty();
    }

    @Test
    void redeemRejectsUnknownCode() {
        CouponService service = new CouponService(CLOCK);
        assertThatThrownBy(() -> service.redeem("CPN-nope", ctx("acme", Money.of("10.00", "CNY"))))
                .isInstanceOf(CouponNotFoundException.class)
                .hasMessageContaining("CPN-nope");
    }

    @Test
    void redeemRejectsExpiredCoupon() {
        CouponService service = new CouponService(CLOCK);
        service.registerTemplate(template("t1", 5, null));
        Coupon coupon = service.issue("t1", "u1");
        service.expire(coupon.code());
        assertThatThrownBy(
                        () -> service.redeem(coupon.code(), ctx("acme", Money.of("10.00", "CNY"))))
                .isInstanceOf(PromotionExpiredException.class);
    }

    @Test
    void redeemRejectsPastExpiryTime() {
        CouponService service =
                new CouponService(Clock.fixed(NOW.plusSeconds(7200), ZoneOffset.UTC));
        service.registerTemplate(template("t1", 5, null));
        Coupon coupon = service.issue("t1", "u1");
        Cart cart =
                Cart.of(
                        "acme",
                        "u1",
                        0,
                        "APP",
                        List.of(new CartItem("sku-1", "book", Money.of("10.00", "CNY"), 1)));
        PromotionContext late = PromotionContext.builder(cart).now(NOW.plusSeconds(7200)).build();
        assertThat(coupon.isUsable(NOW.plusSeconds(7200))).isFalse();
        assertThatThrownBy(() -> service.redeem(coupon.code(), late))
                .isInstanceOf(PromotionExpiredException.class);
    }

    @Test
    void redeemRejectsOtherTenant() {
        CouponService service = new CouponService(CLOCK);
        service.registerTemplate(template("t1", 5, null));
        Coupon coupon = service.issue("t1", "u1");
        assertThatThrownBy(
                        () ->
                                service.redeem(
                                        coupon.code(), ctx("globex", Money.of("10.00", "CNY"))))
                .isInstanceOf(PromotionNotApplicableException.class)
                .hasMessageContaining("不属于租户");
    }

    @Test
    void redeemRejectsBelowThreshold() {
        CouponService service = new CouponService(CLOCK);
        service.registerTemplate(template("t1", 5, Money.of("100.00", "CNY")));
        Coupon coupon = service.issue("t1", "u1");
        assertThatThrownBy(
                        () -> service.redeem(coupon.code(), ctx("acme", Money.of("50.00", "CNY"))))
                .isInstanceOf(PromotionNotApplicableException.class)
                .hasMessageContaining("门槛");
    }

    @Test
    void expireMarksCouponExpired() {
        CouponService service = new CouponService(CLOCK);
        service.registerTemplate(template("t1", 5, null));
        Coupon coupon = service.issue("t1", "u1");
        service.expire(coupon.code());
        assertThat(service.find(coupon.code()).get().status()).isEqualTo(CouponStatus.EXPIRED);
        service.expire(coupon.code());
        assertThat(service.find(coupon.code()).get().status()).isEqualTo(CouponStatus.EXPIRED);
    }

    @Test
    void expireRejectsUsedAndUnknownCoupons() {
        CouponService service = new CouponService(CLOCK);
        service.registerTemplate(template("t1", 5, null));
        Coupon coupon = service.issue("t1", "u1");
        service.redeem(coupon.code(), ctx("acme", Money.of("10.00", "CNY")));
        assertThatThrownBy(() -> service.expire(coupon.code()))
                .isInstanceOf(CouponAlreadyUsedException.class);
        assertThatThrownBy(() -> service.expire("CPN-nope"))
                .isInstanceOf(CouponNotFoundException.class);
    }

    @Test
    void issueRejectsNullTemplateId() {
        CouponService service = new CouponService(CLOCK);
        assertThatThrownBy(() -> service.issue(null, "u1"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.expire(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void capacitiesHavePositiveConstructorGuard() {
        assertThatThrownBy(() -> new CouponService(CLOCK, 0, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CouponService(CLOCK, 10, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void templateOverflowEvictsOldestTemplateAndItsIssuedCount() {
        int maxTemplates = 2;
        CouponService service = new CouponService(CLOCK, maxTemplates, 100);
        service.registerTemplate(template("t1", 5, null));
        service.registerTemplate(template("t2", 5, null));
        service.issue("t1", "u1");
        service.issue("t2", "u1");
        assertThat(service.issuedCount("t1")).isEqualTo(1);

        // 发券会把模板刷到队尾：两次发券后队首回到 t1，所以第 3 张模板挤掉的是 t1 连同它的发放计数。
        service.registerTemplate(template("t3", 5, null));
        assertThat(service.template("t1")).isEmpty();
        assertThat(service.issuedCount("t1")).isZero();
        assertThat(service.template("t2")).isPresent();
        assertThat(service.template("t3")).isPresent();
    }

    @Test
    void couponOverflowEvictsTerminalCouponFirst() {
        int maxCoupons = 3;
        CouponService service = new CouponService(CLOCK, 10, maxCoupons);
        service.registerTemplate(template("t1", 10, null));

        Coupon first = service.issue("t1", "u1");
        Coupon unusedSecond = service.issue("t1", "u2");
        // 把最旧的那张用掉，让它进入终态，成为下一个被淘汰的候选。
        service.redeem(first.code(), ctx("acme", Money.of("10.00", "CNY")));
        assertThat(service.isRedeemed(first.code())).isTrue();

        service.issue("t1", "u3");
        Coupon fourth = service.issue("t1", "u4");
        assertThat(service.find(fourth.code())).isPresent();
        assertThat(service.find(unusedSecond.code())).isPresent();
        // 进入终态的第一张被挤掉；未核销的券优先保留，重复核销保护不丢。
        assertThat(service.find(first.code())).isEmpty();
        assertThat(service.isRedeemed(first.code())).isFalse();
    }

    @Test
    void couponOverflowFallsBackToUnusedCouponWhenNoTerminalOne() {
        int maxCoupons = 2;
        CouponService service = new CouponService(CLOCK, 10, maxCoupons);
        service.registerTemplate(template("t1", 10, null));
        Coupon first = service.issue("t1", "u1");
        service.issue("t1", "u2");
        Coupon third = service.issue("t1", "u3");
        // 没有任何终态券可淘汰，这时只能丢最旧的未核销券。
        assertThat(service.find(first.code())).isEmpty();
        assertThat(service.find(third.code())).isPresent();
    }
}

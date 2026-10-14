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

import io.github.nephets.promokit.core.Money;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-12-31T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");

    private static Coupon template() {
        return new Coupon(
                "CPN-1",
                "t1",
                "acme",
                "u1",
                Money.of("15.00", "CNY"),
                Money.of("100.00", "CNY"),
                END,
                CouponStatus.UNUSED);
    }

    @Test
    void templateValidityIsHalfOpen() {
        CouponTemplate template =
                new CouponTemplate(
                        "t1",
                        "acme",
                        "新人券",
                        Money.of("15.00", "CNY"),
                        Money.of("100.00", "CNY"),
                        START,
                        END,
                        10);
        assertThat(template.isValidAt(START)).isTrue();
        assertThat(template.isValidAt(END)).isFalse();
        assertThat(template.totalQuantity()).isEqualTo(10);
        assertThat(template.threshold()).isEqualTo(Money.of("100.00", "CNY"));
        assertThat(template).hasToString("CouponTemplate{t1, 新人券, 面额 15.00 CNY}");
    }

    @Test
    void noThresholdTemplateHasNullThreshold() {
        CouponTemplate template =
                CouponTemplate.noThreshold(
                        "t1", "acme", "无门槛券", Money.of("5.00", "CNY"), START, END, 3);
        assertThat(template.threshold()).isNull();
        assertThat(template.faceValue()).isEqualTo(Money.of("5.00", "CNY"));
    }

    @Test
    void templateRejectsInvalidInput() {
        assertThatThrownBy(
                        () ->
                                new CouponTemplate(
                                        "t1",
                                        "acme",
                                        "券",
                                        Money.of("5.00", "CNY"),
                                        null,
                                        START,
                                        END,
                                        -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new CouponTemplate(
                                        null,
                                        "acme",
                                        "券",
                                        Money.of("5.00", "CNY"),
                                        null,
                                        START,
                                        END,
                                        1))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CouponTemplate("t1", "acme", "券", null, null, START, END, 1))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void couponStatusSwitchReturnsNewObject() {
        Coupon coupon = template();
        Coupon used = coupon.withStatus(CouponStatus.USED);
        assertThat(coupon.status()).isEqualTo(CouponStatus.UNUSED);
        assertThat(used.status()).isEqualTo(CouponStatus.USED);
        assertThat(used.code()).isEqualTo(coupon.code());
        assertThat(used.faceValue()).isEqualTo(coupon.faceValue());
        assertThat(used).hasToString("Coupon{CPN-1, USED, 面额 15.00 CNY}");
    }

    @Test
    void couponUsabilityDependsOnStatusAndExpiry() {
        assertThat(template().isUsable(NOW)).isTrue();
        assertThat(template().isUsable(END)).isFalse();
        assertThat(template().withStatus(CouponStatus.USED).isUsable(NOW)).isFalse();
        assertThat(template().withStatus(CouponStatus.EXPIRED).isUsable(NOW)).isFalse();
        assertThat(template().expireAt()).isEqualTo(END);
        assertThat(template().templateId()).isEqualTo("t1");
        assertThat(template().ownerId()).isEqualTo("u1");
        assertThatThrownBy(
                        () ->
                                new Coupon(
                                        "CPN-1",
                                        "t1",
                                        "acme",
                                        "u1",
                                        Money.of("1.00", "CNY"),
                                        null,
                                        null,
                                        CouponStatus.UNUSED))
                .isInstanceOf(NullPointerException.class);
    }
}

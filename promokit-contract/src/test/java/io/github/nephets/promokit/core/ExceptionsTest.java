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

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionsTest {

    private static final RuntimeException CAUSE = new IllegalStateException("root cause");

    @Test
    void promotionNotApplicableCarriesMessage() {
        assertThat(new PromotionNotApplicableException("门槛未达到"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("门槛未达到");
        assertThat(new PromotionNotApplicableException("门槛未达到", CAUSE)).hasCause(CAUSE);
    }

    @Test
    void quotaExhaustedCarriesMessage() {
        assertThat(new QuotaExhaustedException("名额已用尽")).hasMessage("名额已用尽");
        assertThat(new QuotaExhaustedException("名额已用尽", CAUSE)).hasCause(CAUSE);
    }

    @Test
    void couponAlreadyUsedExposesCode() {
        CouponAlreadyUsedException ex = new CouponAlreadyUsedException("CPN-1");
        assertThat(ex.code()).isEqualTo("CPN-1");
        assertThat(ex).hasMessageContaining("CPN-1");
        assertThat(new CouponAlreadyUsedException("CPN-1", CAUSE).code()).isEqualTo("CPN-1");
    }

    @Test
    void couponNotFoundExposesCode() {
        CouponNotFoundException ex = new CouponNotFoundException("CPN-2");
        assertThat(ex.code()).isEqualTo("CPN-2");
        assertThat(ex).hasMessageContaining("不存在");
        assertThat(new CouponNotFoundException("CPN-2", CAUSE)).hasCause(CAUSE);
    }

    @Test
    void promotionExpiredExposesTargetId() {
        PromotionExpiredException ex = new PromotionExpiredException("p1");
        assertThat(ex.targetId()).isEqualTo("p1");
        assertThat(ex).hasMessageContaining("已过期");
        assertThat(new PromotionExpiredException("p1", CAUSE)).hasCause(CAUSE);
    }

    @Test
    void currencyMismatchExposesBothCurrencies() {
        CurrencyMismatchException ex = new CurrencyMismatchException("CNY", "USD");
        assertThat(ex.expected()).isEqualTo("CNY");
        assertThat(ex.actual()).isEqualTo("USD");
        assertThat(new CurrencyMismatchException("CNY", "USD", CAUSE)).hasCause(CAUSE);
    }
}

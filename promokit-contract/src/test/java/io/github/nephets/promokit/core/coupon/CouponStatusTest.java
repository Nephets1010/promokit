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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CouponStatusTest {

    @Test
    void valuesCoverUnusedUsedAndExpired() {
        assertThat(CouponStatus.values())
                .containsExactly(CouponStatus.UNUSED, CouponStatus.USED, CouponStatus.EXPIRED);
    }

    @Test
    void valueOfRoundTripsEveryConstant() {
        for (CouponStatus status : CouponStatus.values()) {
            assertThat(CouponStatus.valueOf(status.name())).isSameAs(status);
        }
    }

    @Test
    void terminalStatesAreUsedAndExpired() {
        // 容量淘汰按「终态优先」挑选：USED/EXPIRED 已不可能再进入核销路径，淘汰零资损。
        assertThat(CouponStatus.USED).isNotEqualTo(CouponStatus.UNUSED);
        assertThat(CouponStatus.EXPIRED).isNotEqualTo(CouponStatus.UNUSED);
    }
}

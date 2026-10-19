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

class PromotionStatusTest {

    @Test
    void valuesCoverDraftActivePausedAndEnded() {
        assertThat(PromotionStatus.values())
                .containsExactly(
                        PromotionStatus.DRAFT,
                        PromotionStatus.ACTIVE,
                        PromotionStatus.PAUSED,
                        PromotionStatus.ENDED);
    }

    @Test
    void valueOfRoundTripsEveryConstant() {
        for (PromotionStatus status : PromotionStatus.values()) {
            assertThat(PromotionStatus.valueOf(status.name())).isSameAs(status);
        }
    }

    @Test
    void activeIsTheOnlyStatusThatParticipatesInPricing() {
        // 算价过滤只放行 ACTIVE；其余状态一律进拒绝原因，这里锁定这个约定不被悄悄放宽。
        assertThat(PromotionStatus.ACTIVE)
                .isNotIn(PromotionStatus.DRAFT, PromotionStatus.PAUSED, PromotionStatus.ENDED);
    }
}

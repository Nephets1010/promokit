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

class PromotionTypeTest {

    @Test
    void valuesCoverFiveKinds() {
        assertThat(PromotionType.values())
                .containsExactly(
                        PromotionType.FULL_REDUCTION,
                        PromotionType.DISCOUNT,
                        PromotionType.TIERED,
                        PromotionType.COUPON,
                        PromotionType.FLASH_SALE);
    }

    @Test
    void valueOfRoundTripsEveryConstant() {
        for (PromotionType type : PromotionType.values()) {
            assertThat(PromotionType.valueOf(type.name())).isSameAs(type);
        }
    }
}

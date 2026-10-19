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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SuggestedPromotionTest {

    @Test
    void valueObjectKeepsFields() {
        SuggestedPromotion suggestion =
                new SuggestedPromotion(
                        PromotionType.FULL_REDUCTION,
                        Money.of("100.00", "CNY"),
                        Money.of("10.00", "CNY"),
                        "按当前客单价提档");
        assertThat(suggestion.type()).isEqualTo(PromotionType.FULL_REDUCTION);
        assertThat(suggestion.threshold()).isEqualTo(Money.of("100.00", "CNY"));
        assertThat(suggestion.reduction()).isEqualTo(Money.of("10.00", "CNY"));
        assertThat(suggestion.reason()).isEqualTo("按当前客单价提档");
        assertThat(suggestion).hasToString("FULL_REDUCTION：满 100.00 CNY 减 10.00 CNY（按当前客单价提档）");
        assertThat(suggestion)
                .isEqualTo(
                        new SuggestedPromotion(
                                PromotionType.FULL_REDUCTION,
                                Money.of("100.00", "CNY"),
                                Money.of("10.00", "CNY"),
                                "按当前客单价提档"));
        assertThat(suggestion.hashCode())
                .isEqualTo(
                        new SuggestedPromotion(
                                        PromotionType.FULL_REDUCTION,
                                        Money.of("100.00", "CNY"),
                                        Money.of("10.00", "CNY"),
                                        "按当前客单价提档")
                                .hashCode());
        assertThat(suggestion).isNotEqualTo("FULL_REDUCTION");
    }

    @Test
    void nullReasonBecomesEmptyAndNullFieldsRejected() {
        SuggestedPromotion suggestion =
                new SuggestedPromotion(
                        PromotionType.COUPON,
                        Money.of("10.00", "CNY"),
                        Money.of("1.00", "CNY"),
                        null);
        assertThat(suggestion.reason()).isEmpty();
        assertThatThrownBy(
                        () ->
                                new SuggestedPromotion(
                                        null,
                                        Money.of("10.00", "CNY"),
                                        Money.of("1.00", "CNY"),
                                        "x"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () ->
                                new SuggestedPromotion(
                                        PromotionType.COUPON, null, Money.of("1.00", "CNY"), "x"))
                .isInstanceOf(NullPointerException.class);
    }
}

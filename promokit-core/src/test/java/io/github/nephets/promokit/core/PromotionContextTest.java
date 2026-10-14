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

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromotionContextTest {

    private static Cart cart() {
        return Cart.of(
                "acme",
                "u1",
                1,
                "APP",
                List.of(new CartItem("sku-1", "book", Money.of("30.00", "CNY"), 1)));
    }

    @Test
    void defaultsComeFromCart() {
        PromotionContext ctx = PromotionContext.builder(cart()).build();
        assertThat(ctx.tenantId()).isEqualTo("acme");
        assertThat(ctx.userId()).isEqualTo("u1");
        assertThat(ctx.channel()).isEqualTo("APP");
        assertThat(ctx.memberLevel()).isZero();
        assertThat(ctx.newCustomer()).isFalse();
        assertThat(ctx.now()).isNotNull();
    }

    @Test
    void explicitValuesWin() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        PromotionContext ctx =
                PromotionContext.builder(cart())
                        .tenantId("globex")
                        .userId("u2")
                        .memberLevel(3)
                        .channel("H5")
                        .now(now)
                        .newCustomer(true)
                        .build();
        assertThat(ctx.tenantId()).isEqualTo("globex");
        assertThat(ctx.userId()).isEqualTo("u2");
        assertThat(ctx.memberLevel()).isEqualTo(3);
        assertThat(ctx.channel()).isEqualTo("H5");
        assertThat(ctx.now()).isEqualTo(now);
        assertThat(ctx.newCustomer()).isTrue();
        assertThat(ctx.cart()).isNotNull();
    }

    @Test
    void attributesAreKeptAndImmutable() {
        PromotionContext ctx =
                PromotionContext.builder(cart())
                        .attribute("city", "shanghai")
                        .attributes(Map.of("device", "ios"))
                        .build();
        assertThat(ctx.attributes())
                .containsEntry("city", "shanghai")
                .containsEntry("device", "ios");
        assertThat(ctx.attribute("city", "unknown")).isEqualTo("shanghai");
        assertThat(ctx.attribute("missing", "unknown")).isEqualTo("unknown");
        assertThatThrownBy(() -> ctx.attributes().put("k", "v"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(PromotionContext.builder(cart()).attributes(null).build().attributes())
                .isEmpty();
    }

    @Test
    void cartAndNowAreRequired() {
        assertThatThrownBy(() -> PromotionContext.builder(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PromotionContext.builder(cart()).now(null))
                .isInstanceOf(NullPointerException.class);
    }
}

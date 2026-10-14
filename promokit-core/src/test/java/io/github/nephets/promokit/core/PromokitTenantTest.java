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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromokitTenantTest {

    @AfterEach
    void tearDown() {
        PromokitTenant.clear();
    }

    @Test
    void setCurrentClear() {
        assertThat(PromokitTenant.isSet()).isFalse();
        assertThat(PromokitTenant.current()).isNull();
        PromokitTenant.set("acme");
        assertThat(PromokitTenant.isSet()).isTrue();
        assertThat(PromokitTenant.current()).isEqualTo("acme");
        PromokitTenant.clear();
        assertThat(PromokitTenant.current()).isNull();
    }

    @Test
    void requireThrowsWhenTenantMissing() {
        assertThatThrownBy(PromokitTenant::require)
                .isInstanceOf(PromotionNotApplicableException.class)
                .hasMessageContaining("未设置租户");
        PromokitTenant.set("acme");
        assertThat(PromokitTenant.require()).isEqualTo("acme");
    }

    @Test
    void runWithRestoresPreviousTenant() {
        PromokitTenant.set("outer");
        PromokitTenant.runWith(
                "inner", () -> assertThat(PromokitTenant.current()).isEqualTo("inner"));
        assertThat(PromokitTenant.current()).isEqualTo("outer");
    }

    @Test
    void runWithRestoresOnException() {
        PromokitTenant.set("outer");
        assertThatThrownBy(
                        () ->
                                PromokitTenant.runWith(
                                        "inner",
                                        () -> {
                                            throw new IllegalStateException("boom");
                                        }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(PromokitTenant.current()).isEqualTo("outer");
    }

    @Test
    void callWithReturnsValue() throws Exception {
        String result = PromokitTenant.callWith("acme", () -> PromokitTenant.require());
        assertThat(result).isEqualTo("acme");
        assertThat(PromokitTenant.current()).isNull();
    }

    @Test
    void nestedRunWithKeepsOuterValueAfterInnerFinishes() {
        PromokitTenant.runWith(
                "outer",
                () -> {
                    PromokitTenant.runWith(
                            "inner", () -> assertThat(PromokitTenant.current()).isEqualTo("inner"));
                    assertThat(PromokitTenant.current()).isEqualTo("outer");
                });
        assertThat(PromokitTenant.isSet()).isFalse();
    }
}

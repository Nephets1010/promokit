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
package io.github.nephets.promokit.starter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PromokitPropertiesTest {

    @Test
    void defaultsAreCnyHeaderAndThree() {
        PromokitProperties properties = new PromokitProperties();
        assertThat(properties.getDefaultCurrency()).isEqualTo("CNY");
        assertThat(properties.getTenantHeader()).isEqualTo("X-Tenant-Id");
        assertThat(properties.getMaxStackDepth()).isEqualTo(3);
        assertThat(PromokitProperties.class.getAnnotation(ConfigurationProperties.class).prefix())
                .isEqualTo("promokit");
    }

    @Test
    void settersRoundTrip() {
        PromokitProperties properties = new PromokitProperties();
        properties.setDefaultCurrency("USD");
        properties.setTenantHeader("X-Shop-Id");
        properties.setMaxStackDepth(5);
        assertThat(properties.getDefaultCurrency()).isEqualTo("USD");
        assertThat(properties.getTenantHeader()).isEqualTo("X-Shop-Id");
        assertThat(properties.getMaxStackDepth()).isEqualTo(5);
    }

    @Test
    void bindsFromEnvironment() {
        Map<String, Object> source = new HashMap<>();
        source.put("promokit.default-currency", "USD");
        source.put("promokit.tenant-header", "X-Shop-Id");
        source.put("promokit.max-stack-depth", 2);
        source.put("promokit.default-tenant-id", "acme");
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", source));
        PromokitProperties bound =
                Binder.get(environment).bind("promokit", PromokitProperties.class).get();
        assertThat(bound.getDefaultCurrency()).isEqualTo("USD");
        assertThat(bound.getTenantHeader()).isEqualTo("X-Shop-Id");
        assertThat(bound.getMaxStackDepth()).isEqualTo(2);
        assertThat(bound.getDefaultTenantId()).isEqualTo("acme");
    }

    @Test
    void defaultTenantIdFallsBackToConstant() {
        PromokitProperties properties = new PromokitProperties();
        assertThat(properties.getDefaultTenantId())
                .isEqualTo(PromotionController.DEFAULT_TENANT_ID);
        properties.setDefaultTenantId("acme");
        assertThat(properties.getDefaultTenantId()).isEqualTo("acme");
    }

    @Test
    void invalidSetterValuesKeepLastValidValue() {
        PromokitProperties properties = new PromokitProperties();
        properties.setDefaultCurrency("USD");
        properties.setMaxStackDepth(5);
        properties.setTenantHeader("X-Shop-Id");
        properties.setDefaultTenantId("acme");

        properties.setDefaultCurrency("  ");
        properties.setMaxStackDepth(0);
        properties.setTenantHeader(null);
        properties.setDefaultTenantId("");

        assertThat(properties.getDefaultCurrency()).isEqualTo("USD");
        assertThat(properties.getMaxStackDepth()).isEqualTo(5);
        assertThat(properties.getTenantHeader()).isEqualTo("X-Shop-Id");
        assertThat(properties.getDefaultTenantId()).isEqualTo("acme");
    }
}

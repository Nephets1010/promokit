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

import io.github.nephets.promokit.core.InMemoryQuotaStore;
import io.github.nephets.promokit.core.PromotionRegistry;
import io.github.nephets.promokit.core.coupon.CouponService;

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

    @Test
    void capacitySettersRejectNonPositiveAndKeepLastValid() {
        // 四个容量字段共用 isPositiveCapacity 守卫：<= 0 时告警并保留上一个有效值，而不是让存储构造期抛异常。
        PromokitProperties properties = new PromokitProperties();
        properties.setMaxTemplates(50);
        properties.setMaxCoupons(500);
        properties.setRegistryCapacity(200);
        properties.setQuotaCapacity(1000L);

        properties.setMaxTemplates(0);
        properties.setMaxCoupons(-1);
        properties.setRegistryCapacity(0);
        properties.setQuotaCapacity(-5L);

        assertThat(properties.getMaxTemplates()).isEqualTo(50);
        assertThat(properties.getMaxCoupons()).isEqualTo(500);
        assertThat(properties.getRegistryCapacity()).isEqualTo(200);
        assertThat(properties.getQuotaCapacity()).isEqualTo(1000L);
    }

    @Test
    void defaultCapacitiesMatchReferencedConstants() {
        // 默认值必须和下游存储的安全默认值对齐，否则配置语义漂移。
        PromokitProperties properties = new PromokitProperties();
        assertThat(properties.getMaxTemplates()).isEqualTo(CouponService.DEFAULT_MAX_TEMPLATES);
        assertThat(properties.getMaxCoupons()).isEqualTo(CouponService.DEFAULT_MAX_COUPONS);
        assertThat(properties.getRegistryCapacity()).isEqualTo(PromotionRegistry.DEFAULT_CAPACITY);
        assertThat(properties.getQuotaCapacity()).isEqualTo(InMemoryQuotaStore.DEFAULT_CAPACITY);
    }

    @Test
    void quotaCapacityAcceptsLargePositiveLongDistinctFromIntCapacities() {
        // quota-capacity 是 long 字段，单独验证其正数路径与不覆盖已设值语义。
        PromokitProperties properties = new PromokitProperties();
        properties.setQuotaCapacity(Long.MAX_VALUE);
        assertThat(properties.getQuotaCapacity()).isEqualTo(Long.MAX_VALUE);
        properties.setQuotaCapacity(0L);
        assertThat(properties.getQuotaCapacity()).isEqualTo(Long.MAX_VALUE);
    }
}

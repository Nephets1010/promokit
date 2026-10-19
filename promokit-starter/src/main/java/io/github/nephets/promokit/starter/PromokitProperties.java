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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** promokit 配置属性，前缀 {@code promokit}。所有可部署相关的取值都可外置，并带安全默认值。 */
@ConfigurationProperties(prefix = "promokit")
public class PromokitProperties {

    private static final Logger log = LoggerFactory.getLogger(PromokitProperties.class);

    /** 解析租户的请求头名称的默认值。 */
    public static final String DEFAULT_TENANT_HEADER = "X-Tenant-Id";

    /** 默认币种。 */
    public static final String DEFAULT_CURRENCY = "CNY";

    /** 默认最多叠加的活动数。 */
    public static final int DEFAULT_MAX_STACK_DEPTH = 3;

    /** 请求未指定币种时使用的默认币种。默认 CNY。 */
    private String defaultCurrency = DEFAULT_CURRENCY;

    /** 解析租户的请求头名称。默认 {@code X-Tenant-Id}。 */
    private String tenantHeader = DEFAULT_TENANT_HEADER;

    /** 一次算价最多叠加的活动数。默认 3。 */
    private int maxStackDepth = DEFAULT_MAX_STACK_DEPTH;

    /** 请求与线程上下文都没有租户时的兜底租户 id。默认 {@code default}。 */
    private String defaultTenantId = PromotionController.DEFAULT_TENANT_ID;

    /** 券模板注册表容量上限，对应 {@code promokit.max-templates}。默认 10000。 */
    private int maxTemplates = CouponService.DEFAULT_MAX_TEMPLATES;

    /** 券实例容量上限，对应 {@code promokit.max-coupons}。默认 200000。 */
    private int maxCoupons = CouponService.DEFAULT_MAX_COUPONS;

    /** 活动注册表容量上限，对应 {@code promokit.registry-capacity}。默认 10000。 */
    private int registryCapacity = PromotionRegistry.DEFAULT_CAPACITY;

    /** 名额存储容量上限（逻辑条目，跨全部计数表），对应 {@code promokit.quota-capacity}。默认 1000000。 */
    private long quotaCapacity = InMemoryQuotaStore.DEFAULT_CAPACITY;

    public String getDefaultCurrency() {
        return defaultCurrency;
    }

    public void setDefaultCurrency(String defaultCurrency) {
        // 配错不算致命错误：保留上一个有效值而不是让算价在 Money.of 处才炸，但要留痕。
        if (isBlank(defaultCurrency)) {
            logInvalid("default-currency", defaultCurrency);
            return;
        }
        this.defaultCurrency = defaultCurrency;
    }

    public String getTenantHeader() {
        return tenantHeader;
    }

    public void setTenantHeader(String tenantHeader) {
        if (isBlank(tenantHeader)) {
            logInvalid("tenant-header", tenantHeader);
            return;
        }
        this.tenantHeader = tenantHeader;
    }

    public int getMaxStackDepth() {
        return maxStackDepth;
    }

    public void setMaxStackDepth(int maxStackDepth) {
        // <= 0 会让 PriceEngine 构造直接抛 IllegalArgumentException，这里挡在配置边界上。
        if (maxStackDepth < 1) {
            log.warn(
                    "promokit.max-stack-depth={} is invalid, keeping {}",
                    maxStackDepth,
                    this.maxStackDepth);
            return;
        }
        this.maxStackDepth = maxStackDepth;
    }

    public String getDefaultTenantId() {
        return defaultTenantId;
    }

    public void setDefaultTenantId(String defaultTenantId) {
        if (isBlank(defaultTenantId)) {
            logInvalid("default-tenant-id", defaultTenantId);
            return;
        }
        this.defaultTenantId = defaultTenantId;
    }

    public int getMaxTemplates() {
        return maxTemplates;
    }

    public void setMaxTemplates(int maxTemplates) {
        // 容量 <= 0 会让对应存储构造直接抛 IllegalArgumentException，挡在配置边界上并保留上一个有效值。
        if (!isPositiveCapacity("max-templates", maxTemplates)) {
            return;
        }
        this.maxTemplates = maxTemplates;
    }

    public int getMaxCoupons() {
        return maxCoupons;
    }

    public void setMaxCoupons(int maxCoupons) {
        if (!isPositiveCapacity("max-coupons", maxCoupons)) {
            return;
        }
        this.maxCoupons = maxCoupons;
    }

    public int getRegistryCapacity() {
        return registryCapacity;
    }

    public void setRegistryCapacity(int registryCapacity) {
        if (!isPositiveCapacity("registry-capacity", registryCapacity)) {
            return;
        }
        this.registryCapacity = registryCapacity;
    }

    public long getQuotaCapacity() {
        return quotaCapacity;
    }

    public void setQuotaCapacity(long quotaCapacity) {
        if (!isPositiveCapacity("quota-capacity", quotaCapacity)) {
            return;
        }
        this.quotaCapacity = quotaCapacity;
    }

    /**
     * 容量类配置的公共守卫：必须为正数，否则告警并保留上一个有效值。
     *
     * <p>为什么容量也要挡在边界上：这些上限是长生命周期容器唯一的防泄漏闸门，配成 0 或负数会让存储 在构造期直接抛异常导致应用起不来，还不如保留安全默认值、把错误配置留在日志里。
     */
    private boolean isPositiveCapacity(String key, long value) {
        if (value < 1) {
            log.warn(
                    "promokit.{}={} is invalid, keeping {}",
                    key,
                    value,
                    switch (key) {
                        case "max-templates" -> maxTemplates;
                        case "max-coupons" -> maxCoupons;
                        case "registry-capacity" -> registryCapacity;
                        default -> quotaCapacity;
                    });
            return false;
        }
        return true;
    }

    private static void logInvalid(String key, Object value) {
        log.warn("promokit.{}=[{}] is blank, keeping previous value", key, value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

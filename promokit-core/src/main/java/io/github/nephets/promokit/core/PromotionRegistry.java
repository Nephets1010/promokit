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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 活动注册表：按租户存放活动，同名活动重复注册视为覆盖。
 *
 * <p>活动总数是有限的：超过 {@link #capacity()} 时按最近使用顺序淘汰最旧的一条，并打 WARN 日志。运营后台可以长期开放
 * 注册接口，放任注册表无限增长会变成内存泄漏点，所以这里给它一个上限。
 */
public final class PromotionRegistry {

    /** 默认最多容纳的活动条数。 */
    public static final int DEFAULT_CAPACITY = 10_000;

    private static final Logger log = LoggerFactory.getLogger(PromotionRegistry.class);

    private final Map<String, Map<String, Promotion>> byTenant = new ConcurrentHashMap<>();

    private final int capacity;

    /** 保护 {@link #recency}：LRU 顺序与溢出淘汰都只在持锁时改。 */
    private final Object lock = new Object();

    /** 活动级 LRU：key 为 {@code tenantId|promotionId}，溢出时从头淘汰。 */
    private final LinkedHashMap<String, Boolean> recency = new LinkedHashMap<>(64, 0.75f, true);

    /** 上限为 {@link #DEFAULT_CAPACITY} 条活动。 */
    public PromotionRegistry() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * 构造一个带容量上限的注册表。
     *
     * @param capacity 活动条数上限，必须为正数
     */
    public PromotionRegistry(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1, got " + capacity);
        }
        this.capacity = capacity;
    }

    /** 活动条数上限。 */
    public int capacity() {
        return capacity;
    }

    /** 注册或覆盖一个活动。 */
    public void register(Promotion promotion) {
        Objects.requireNonNull(promotion, "promotion must not be null");
        String tenantId = requireNonBlank(promotion.tenantId(), "tenantId");
        String promotionId = requireNonBlank(promotion.promotionId(), "promotionId");
        byTenant.computeIfAbsent(tenantId, key -> new ConcurrentHashMap<>())
                .put(promotionId, promotion);
        String key = compositeKey(tenantId, promotionId);
        synchronized (lock) {
            touch(key);
        }
        log.info(
                "register promotion tenantId={} promotionId={} type={} status={}",
                tenantId,
                promotionId,
                promotion.type(),
                promotion.status());
    }

    /** 移除一个活动。 */
    public void unregister(String tenantId, String promotionId) {
        String tenant = requireNonBlank(tenantId, "tenantId");
        String id = requireNonBlank(promotionId, "promotionId");
        Map<String, Promotion> tenantMap = byTenant.get(tenant);
        if (tenantMap == null) {
            log.debug(
                    "unregister skipped tenantId={} promotionId={} tenant not registered",
                    tenant,
                    id);
            return;
        }
        Promotion removed = tenantMap.remove(id);
        synchronized (lock) {
            recency.remove(compositeKey(tenant, id));
        }
        if (removed == null) {
            log.debug(
                    "unregister skipped tenantId={} promotionId={} promotion not found",
                    tenant,
                    id);
            return;
        }
        log.info("unregister promotion tenantId={} promotionId={}", tenant, id);
    }

    /** 按租户与活动 id 查找。 */
    public Optional<Promotion> find(String tenantId, String promotionId) {
        String tenant = requireNonBlank(tenantId, "tenantId");
        String id = requireNonBlank(promotionId, "promotionId");
        Map<String, Promotion> tenantMap = byTenant.get(tenant);
        Promotion promotion = tenantMap == null ? null : tenantMap.get(id);
        if (promotion != null) {
            String key = compositeKey(tenant, id);
            synchronized (lock) {
                touch(key);
            }
        }
        return Optional.ofNullable(promotion);
    }

    /** 租户下的全部活动，按 priority 升序、活动 id 升序排列。 */
    public List<Promotion> byTenant(String tenantId) {
        String tenant = requireNonBlank(tenantId, "tenantId");
        Map<String, Promotion> tenantMap = byTenant.get(tenant);
        if (tenantMap == null) {
            return List.of();
        }
        return sorted(new ArrayList<>(tenantMap.values()));
    }

    /** 租户下状态为 ACTIVE 且当前时间在窗口内的活动。 */
    public List<Promotion> active(String tenantId, Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        List<Promotion> result = new ArrayList<>();
        for (Promotion promotion : byTenant(tenantId)) {
            if (promotion.isActiveAt(now)) {
                result.add(promotion);
            }
        }
        return result;
    }

    /** 已注册活动总数。 */
    public int size() {
        int total = 0;
        for (Map<String, Promotion> tenant : byTenant.values()) {
            total += tenant.size();
        }
        return total;
    }

    /** 已注册的租户 id。 */
    public Set<String> tenantIds() {
        return Collections.unmodifiableSet(byTenant.keySet());
    }

    /** 清空注册表。 */
    public void clear() {
        byTenant.clear();
        synchronized (lock) {
            recency.clear();
        }
        log.info("clear promotion registry");
    }

    /**
     * 记录一条活动的访问顺序，超出容量时淘汰最旧的一条。
     *
     * <p>必须在 {@code lock} 保护下调用。
     */
    private void touch(String key) {
        recency.put(key, Boolean.TRUE);
        while (recency.size() > capacity) {
            String eldest = recency.keySet().iterator().next();
            recency.remove(eldest);
            evict(eldest);
        }
    }

    /** 淘汰一条活动；租户名下没有活动时顺带移除空的租户表。 */
    private void evict(String composite) {
        int separator = composite.indexOf('|');
        String tenantId = composite.substring(0, separator);
        String promotionId = composite.substring(separator + 1);
        Map<String, Promotion> tenantMap = byTenant.get(tenantId);
        if (tenantMap != null && tenantMap.remove(promotionId) == null) {
            return;
        }
        log.warn(
                "promotion evicted tenantId={} promotionId={} reason=capacity {} exceeded",
                tenantId,
                promotionId,
                capacity);
        if (tenantMap != null && tenantMap.isEmpty()) {
            byTenant.remove(tenantId, tenantMap);
        }
    }

    private static String compositeKey(String tenantId, String promotionId) {
        return tenantId + '|' + promotionId;
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static List<Promotion> sorted(List<Promotion> promotions) {
        promotions.sort(
                Comparator.comparingInt(Promotion::priority).thenComparing(Promotion::promotionId));
        return Collections.unmodifiableList(promotions);
    }
}

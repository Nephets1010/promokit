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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 内存名额存储：按 租户 + 活动 + 用户 计数，线程安全。
 *
 * <p>先用 {@link #registerQuota} 登记名额，未登记的活动视为不限量。预留成功会计入「未确认」用量，confirm 转为已确认， release 退回。confirm 与
 * release 都是幂等的，用量不会变成负数。
 */
public final class InMemoryQuotaStore implements QuotaStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryQuotaStore.class);

    /** 默认容量：约 1_000_000 条逻辑条目。 */
    public static final long DEFAULT_CAPACITY = 1_000_000L;

    private final Object lock = new Object();
    private final long capacity;

    private final Map<String, Integer> totalLimits = new HashMap<>();
    private final Map<String, Integer> userLimits = new HashMap<>();
    private final Map<String, Integer> reservedTotal = new HashMap<>();
    private final Map<String, Integer> confirmedTotal = new HashMap<>();
    private final Map<String, Integer> reservedByUser = new HashMap<>();
    private final Map<String, Integer> confirmedByUser = new HashMap<>();

    /**
     * 全局最近使用顺序：记录所有 6 张表中共用的字符串 key 的出现/访问顺序。 accessOrder=true 时迭代顺序即 LRU，溢出时移除最旧的那一项（从所有表中一并清除）。
     */
    private final LinkedHashMap<String, Boolean> recency = new LinkedHashMap<>(64, 0.75f, true);

    /** 默认容量约 1_000_000 条逻辑条目。 */
    public InMemoryQuotaStore() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * 构造一个带容量上限的存储。
     *
     * @param capacity 逻辑条目上限（跨 6 张表共用），必须为正数
     */
    public InMemoryQuotaStore(long capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
        this.capacity = capacity;
    }

    /**
     * 登记活动的名额。
     *
     * @param tenantId 租户 id
     * @param promotionId 活动 id
     * @param totalQuota 总名额，null 表示不限
     * @param perUserQuota 每人限用次数，null 表示不限
     */
    public void registerQuota(
            String tenantId, String promotionId, Integer totalQuota, Integer perUserQuota) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(promotionId, "promotionId must not be null");
        synchronized (lock) {
            if (totalQuota != null) {
                String key = totalKey(tenantId, promotionId);
                totalLimits.put(key, totalQuota);
                touch(key);
            }
            if (perUserQuota != null) {
                String key = userKey(tenantId, promotionId);
                userLimits.put(key, perUserQuota);
                touch(key);
            }
        }
        log.info(
                "register quota tenantId={} promotionId={} totalQuota={} perUserQuota={}",
                tenantId,
                promotionId,
                totalQuota,
                perUserQuota);
    }

    /** 按活动上配置的名额登记。 */
    public void registerQuota(Promotion promotion) {
        Objects.requireNonNull(promotion, "promotion must not be null");
        registerQuota(
                promotion.tenantId(),
                promotion.promotionId(),
                promotion.totalQuota(),
                promotion.perUserQuota());
    }

    /** 已确认用量（不含未确认的预留）。 */
    public int confirmedTotal(String tenantId, String promotionId) {
        synchronized (lock) {
            return confirmedTotal.getOrDefault(totalKey(tenantId, promotionId), 0);
        }
    }

    /** 清空所有计数与限额。 */
    public void clear() {
        synchronized (lock) {
            totalLimits.clear();
            userLimits.clear();
            reservedTotal.clear();
            confirmedTotal.clear();
            reservedByUser.clear();
            confirmedByUser.clear();
            recency.clear();
        }
        log.info("clear quota store");
    }

    @Override
    public boolean reserve(String tenantId, String promotionId, String userId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(promotionId, "promotionId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        synchronized (lock) {
            String totalK = totalKey(tenantId, promotionId);
            String userK = userKey(tenantId, promotionId, userId);
            int limit = totalLimits.getOrDefault(totalK, Integer.MAX_VALUE);
            if (usedTotalLocked(totalK) >= limit) {
                log.debug(
                        "quota reserve rejected tenantId={} promotionId={} userId={} reason=total"
                                + " exhausted used={} limit={}",
                        tenantId,
                        promotionId,
                        userId,
                        usedTotalLocked(totalK),
                        limit);
                return false;
            }
            int userLimit =
                    userLimits.getOrDefault(userKey(tenantId, promotionId), Integer.MAX_VALUE);
            if (usedByUserLocked(userK) >= userLimit) {
                log.debug(
                        "quota reserve rejected tenantId={} promotionId={} userId={} reason=perUser"
                                + " exhausted used={} limit={}",
                        tenantId,
                        promotionId,
                        userId,
                        usedByUserLocked(userK),
                        userLimit);
                return false;
            }
            reservedTotal.put(totalK, reservedTotal.getOrDefault(totalK, 0) + 1);
            reservedByUser.put(userK, reservedByUser.getOrDefault(userK, 0) + 1);
            touch(totalK);
            touch(userK);
            return true;
        }
    }

    @Override
    public void confirm(String tenantId, String promotionId, String userId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(promotionId, "promotionId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        synchronized (lock) {
            String totalK = totalKey(tenantId, promotionId);
            String userK = userKey(tenantId, promotionId, userId);
            if (reservedTotal.getOrDefault(totalK, 0) > 0) {
                reservedTotal.put(totalK, reservedTotal.get(totalK) - 1);
                confirmedTotal.put(totalK, confirmedTotal.getOrDefault(totalK, 0) + 1);
                touch(totalK);
            }
            if (reservedByUser.getOrDefault(userK, 0) > 0) {
                reservedByUser.put(userK, reservedByUser.get(userK) - 1);
                confirmedByUser.put(userK, confirmedByUser.getOrDefault(userK, 0) + 1);
                touch(userK);
            }
        }
    }

    @Override
    public void release(String tenantId, String promotionId, String userId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(promotionId, "promotionId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        synchronized (lock) {
            String totalK = totalKey(tenantId, promotionId);
            String userK = userKey(tenantId, promotionId, userId);
            decrement(reservedTotal, totalK);
            decrement(reservedByUser, userK);
        }
    }

    @Override
    public int usedByUser(String tenantId, String promotionId, String userId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(promotionId, "promotionId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        synchronized (lock) {
            return usedByUserLocked(userKey(tenantId, promotionId, userId));
        }
    }

    @Override
    public int usedTotal(String tenantId, String promotionId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(promotionId, "promotionId must not be null");
        synchronized (lock) {
            return usedTotalLocked(totalKey(tenantId, promotionId));
        }
    }

    private int usedTotalLocked(String totalK) {
        return reservedTotal.getOrDefault(totalK, 0) + confirmedTotal.getOrDefault(totalK, 0);
    }

    private int usedByUserLocked(String userK) {
        return reservedByUser.getOrDefault(userK, 0) + confirmedByUser.getOrDefault(userK, 0);
    }

    private static void decrement(Map<String, Integer> counters, String key) {
        int current = counters.getOrDefault(key, 0);
        if (current > 0) {
            counters.put(key, current - 1);
        }
    }

    /**
     * 记录 key 的插入/访问时间，并在超过容量上限时淘汰最旧的一条逻辑条目（从所有表中一并移除）。
     *
     * <p>为什么淘汰必须留痕（WARN）：名额表是长生命周期容器，放任增长会拖垮内存，所以必须有界； 但淘汰是有损降级——该 key
     * 登记过的上限随之丢失，之后这个活动会被当成不限量。这属于必须运维 知晓的事件，静默淘汰会让「名额突然失灵」变成无据可查的悬案，因此打 WARN 并写明原因。
     *
     * <p>必须在 {@code lock} 保护下调用。
     */
    private void touch(String key) {
        recency.put(key, Boolean.TRUE);
        while (recency.size() > capacity) {
            String eldest = recency.keySet().iterator().next();
            recency.remove(eldest);
            evictKey(eldest);
            // 淘汰会丢掉这一活动已登记的名额上限，之后该活动会被当成不限量，必须留痕。
            log.warn(
                    "quota entry evicted key={} reason=capacity {} exceeded, limits for this key"
                            + " are dropped",
                    eldest,
                    capacity);
        }
    }

    /**
     * 将一个逻辑 key 从「限额表」中移除（淘汰）。
     *
     * <p>为什么只移除限额、保留用量计数：淘汰的语义是「这个活动登记过的名额上限丢了，之后按不限量放行」 （{@code limit} 退化为 {@code
     * MAX_VALUE}），但已经预留/确认过的用量是历史事实，不能因为名额表淘汰而 清零——否则 {@code usedTotal} 会失真，已发放的券也查不出累计用量。所以 {@code
     * reservedTotal}/ {@code confirmedTotal}/{@code reservedByUser}/{@code confirmedByUser} 一律保留。
     */
    private void evictKey(String key) {
        // 只移除限额：之后该活动按不限量放行。
        totalLimits.remove(key);
        userLimits.remove(key);
        // 故意保留 reservedTotal/confirmedTotal/reservedByUser/confirmedByUser（见上方 Javadoc）。
    }

    private static String totalKey(String tenantId, String promotionId) {
        return tenantId + "|" + promotionId;
    }

    private static String userKey(String tenantId, String promotionId) {
        return tenantId + "|" + promotionId;
    }

    private static String userKey(String tenantId, String promotionId, String userId) {
        return tenantId + "|" + promotionId + "|" + userId;
    }
}

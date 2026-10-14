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

/**
 * 名额存储：活动的总名额与每人限用次数由外部实现持久化，核心只做预留 / 确认 / 回滚。
 *
 * <p>调用顺序是 reserve → confirm（下单成功）或 reserve → release（下单失败）。
 */
public interface QuotaStore {

    /**
     * 预留一次名额。
     *
     * @param tenantId 租户 id
     * @param promotionId 活动 id
     * @param userId 用户 id
     * @return false 表示名额已用尽，此时不得记入用量
     */
    boolean reserve(String tenantId, String promotionId, String userId);

    /**
     * 确认预留，计入最终用量。重复调用无副作用。
     *
     * @param tenantId 租户 id
     * @param promotionId 活动 id
     * @param userId 用户 id
     */
    void confirm(String tenantId, String promotionId, String userId);

    /**
     * 释放预留。重复调用或用量已为 0 时无副作用。
     *
     * @param tenantId 租户 id
     * @param promotionId 活动 id
     * @param userId 用户 id
     */
    void release(String tenantId, String promotionId, String userId);

    /** 某用户在该活动下已用次数（含未确认的预留）。 */
    int usedByUser(String tenantId, String promotionId, String userId);

    /** 该活动已用总次数（含未确认的预留）。 */
    int usedTotal(String tenantId, String promotionId);
}

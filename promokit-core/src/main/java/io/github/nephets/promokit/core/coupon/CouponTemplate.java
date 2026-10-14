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
package io.github.nephets.promokit.core.coupon;

import io.github.nephets.promokit.core.Money;

import java.time.Instant;
import java.util.Objects;

/** 券模板：面额、门槛、有效期与发放总量。 */
public final class CouponTemplate {

    private final String templateId;
    private final String tenantId;
    private final String name;
    private final Money faceValue;
    private final Money threshold;
    private final Instant startTime;
    private final Instant endTime;
    private final int totalQuantity;

    /**
     * @param templateId 模板 id
     * @param tenantId 租户 id
     * @param name 券名称
     * @param faceValue 面额
     * @param threshold 使用门槛，null 表示无门槛
     * @param startTime 有效期开始
     * @param endTime 有效期结束
     * @param totalQuantity 发放总量
     */
    public CouponTemplate(
            String templateId,
            String tenantId,
            String name,
            Money faceValue,
            Money threshold,
            Instant startTime,
            Instant endTime,
            int totalQuantity) {
        this.templateId = Objects.requireNonNull(templateId, "templateId must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.faceValue = Objects.requireNonNull(faceValue, "faceValue must not be null");
        this.threshold = threshold;
        this.startTime = Objects.requireNonNull(startTime, "startTime must not be null");
        this.endTime = Objects.requireNonNull(endTime, "endTime must not be null");
        if (totalQuantity < 0) {
            throw new IllegalArgumentException("totalQuantity must not be negative");
        }
        this.totalQuantity = totalQuantity;
    }

    /** 无门槛券模板。 */
    public static CouponTemplate noThreshold(
            String templateId,
            String tenantId,
            String name,
            Money faceValue,
            Instant startTime,
            Instant endTime,
            int totalQuantity) {
        return new CouponTemplate(
                templateId, tenantId, name, faceValue, null, startTime, endTime, totalQuantity);
    }

    public String templateId() {
        return templateId;
    }

    public String tenantId() {
        return tenantId;
    }

    public String name() {
        return name;
    }

    public Money faceValue() {
        return faceValue;
    }

    /** 使用门槛，null 表示无门槛。 */
    public Money threshold() {
        return threshold;
    }

    public Instant startTime() {
        return startTime;
    }

    public Instant endTime() {
        return endTime;
    }

    public int totalQuantity() {
        return totalQuantity;
    }

    /** 给定时间是否落在有效期内。 */
    public boolean isValidAt(Instant now) {
        return !now.isBefore(startTime) && now.isBefore(endTime);
    }

    @Override
    public String toString() {
        return "CouponTemplate{" + templateId + ", " + name + ", 面额 " + faceValue + "}";
    }
}

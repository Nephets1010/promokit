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

/** 券实例：券码、归属人、面额门槛与状态。不可变，改状态返回新对象。 */
public final class Coupon {

    private final String code;
    private final String templateId;
    private final String tenantId;
    private final String ownerId;
    private final Money faceValue;
    private final Money threshold;
    private final Instant expireAt;
    private final CouponStatus status;

    /**
     * @param code 券码
     * @param templateId 模板 id
     * @param tenantId 租户 id
     * @param ownerId 归属用户 id
     * @param faceValue 面额
     * @param threshold 门槛，null 表示无门槛
     * @param expireAt 过期时间
     * @param status 状态
     */
    public Coupon(
            String code,
            String templateId,
            String tenantId,
            String ownerId,
            Money faceValue,
            Money threshold,
            Instant expireAt,
            CouponStatus status) {
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.templateId = Objects.requireNonNull(templateId, "templateId must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.ownerId = ownerId;
        this.faceValue = Objects.requireNonNull(faceValue, "faceValue must not be null");
        this.threshold = threshold;
        this.expireAt = Objects.requireNonNull(expireAt, "expireAt must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    public String code() {
        return code;
    }

    public String templateId() {
        return templateId;
    }

    public String tenantId() {
        return tenantId;
    }

    public String ownerId() {
        return ownerId;
    }

    public Money faceValue() {
        return faceValue;
    }

    /** 使用门槛，null 表示无门槛。 */
    public Money threshold() {
        return threshold;
    }

    public Instant expireAt() {
        return expireAt;
    }

    public CouponStatus status() {
        return status;
    }

    /** 未过期且未核销。 */
    public boolean isUsable(Instant now) {
        return status == CouponStatus.UNUSED && now.isBefore(expireAt);
    }

    /** 返回指定状态的新券对象。 */
    public Coupon withStatus(CouponStatus newStatus) {
        return new Coupon(
                code, templateId, tenantId, ownerId, faceValue, threshold, expireAt, newStatus);
    }

    @Override
    public String toString() {
        return "Coupon{" + code + ", " + status + ", 面额 " + faceValue + "}";
    }
}

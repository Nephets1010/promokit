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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 算价结果。
 *
 * <p>恒有 {@code subtotal - totalDiscount == payable}，且 payable 不为负。
 */
public final class PriceResult {

    private final Money subtotal;
    private final Money totalDiscount;
    private final Money payable;
    private final List<Discount> applied;
    private final List<String> rejectedReasons;

    public PriceResult(
            Money subtotal,
            Money totalDiscount,
            Money payable,
            List<Discount> applied,
            List<String> rejectedReasons) {
        this.subtotal = Objects.requireNonNull(subtotal, "subtotal must not be null");
        this.totalDiscount =
                Objects.requireNonNull(totalDiscount, "totalDiscount must not be null");
        this.payable = Objects.requireNonNull(payable, "payable must not be null");
        List<Discount> appliedCopy = new ArrayList<>();
        if (applied != null) {
            appliedCopy.addAll(applied);
        }
        this.applied = Collections.unmodifiableList(appliedCopy);
        List<String> reasonsCopy = new ArrayList<>();
        if (rejectedReasons != null) {
            reasonsCopy.addAll(rejectedReasons);
        }
        this.rejectedReasons = Collections.unmodifiableList(reasonsCopy);
    }

    public Money subtotal() {
        return subtotal;
    }

    public Money totalDiscount() {
        return totalDiscount;
    }

    public Money payable() {
        return payable;
    }

    /** 生效的优惠明细，按算价时的选择顺序排列。 */
    public List<Discount> applied() {
        return applied;
    }

    /** 被剔除的活动及原因，用于排查「为什么没享受这个优惠」。 */
    public List<String> rejectedReasons() {
        return rejectedReasons;
    }

    public boolean hasDiscount() {
        return !applied.isEmpty();
    }

    @Override
    public String toString() {
        return "PriceResult{原价 " + subtotal + ", 优惠 " + totalDiscount + ", 应付 " + payable + "}";
    }
}

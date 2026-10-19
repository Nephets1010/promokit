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

import java.util.Objects;

/** 一条活动建议：类型、门槛、优惠额度与理由。 */
public final class SuggestedPromotion {

    private final PromotionType type;
    private final Money threshold;
    private final Money reduction;
    private final String reason;

    /**
     * @param type 建议的活动类型
     * @param threshold 建议门槛
     * @param reduction 建议优惠额度
     * @param reason 建议理由
     */
    public SuggestedPromotion(PromotionType type, Money threshold, Money reduction, String reason) {
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.threshold = Objects.requireNonNull(threshold, "threshold must not be null");
        this.reduction = Objects.requireNonNull(reduction, "reduction must not be null");
        this.reason = reason == null ? "" : reason;
    }

    public PromotionType type() {
        return type;
    }

    public Money threshold() {
        return threshold;
    }

    public Money reduction() {
        return reduction;
    }

    public String reason() {
        return reason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SuggestedPromotion other)) {
            return false;
        }
        return type == other.type
                && threshold.equals(other.threshold)
                && reduction.equals(other.reduction)
                && reason.equals(other.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, threshold, reduction, reason);
    }

    @Override
    public String toString() {
        return type + "：满 " + threshold + " 减 " + reduction + "（" + reason + "）";
    }
}

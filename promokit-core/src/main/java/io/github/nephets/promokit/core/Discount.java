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

/** 一条优惠明细：由哪个活动产生、减了多少、怎么算的。 */
public final class Discount {

    private final String promotionId;
    private final Money amount;
    private final String description;

    /**
     * @param promotionId 活动 id
     * @param amount 优惠金额，非负
     * @param description 说明文案
     */
    public Discount(String promotionId, Money amount, String description) {
        this.promotionId = Objects.requireNonNull(promotionId, "promotionId must not be null");
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        this.description = description == null ? "" : description;
    }

    /** 零优惠，用于策略判定不达标时返回。 */
    public static Discount zero(String promotionId, Money currency, String description) {
        return new Discount(promotionId, Money.zero(currency.currency()), description);
    }

    public String promotionId() {
        return promotionId;
    }

    public Money amount() {
        return amount;
    }

    public String description() {
        return description;
    }

    public boolean isZero() {
        return amount.isZero();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Discount other)) {
            return false;
        }
        return promotionId.equals(other.promotionId)
                && amount.equals(other.amount)
                && description.equals(other.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(promotionId, amount, description);
    }

    @Override
    public String toString() {
        return promotionId + " -" + amount + " (" + description + ")";
    }
}

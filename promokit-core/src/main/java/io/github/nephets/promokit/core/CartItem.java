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

/** 购物车行项：商品、类目、单价与数量，不可变。 */
public final class CartItem {

    private final String skuId;
    private final String categoryId;
    private final Money unitPrice;
    private final int quantity;

    /**
     * @param skuId 商品 id
     * @param categoryId 类目 id
     * @param unitPrice 单价
     * @param quantity 数量，必须大于 0
     */
    public CartItem(String skuId, String categoryId, Money unitPrice, int quantity) {
        this.skuId = requireText(skuId, "skuId");
        this.categoryId = requireText(categoryId, "categoryId");
        this.unitPrice = Objects.requireNonNull(unitPrice, "unitPrice must not be null");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive, got " + quantity);
        }
        this.quantity = quantity;
    }

    /** 便捷构造。 */
    public static CartItem of(String skuId, String categoryId, Money unitPrice, int quantity) {
        return new CartItem(skuId, categoryId, unitPrice, quantity);
    }

    public String skuId() {
        return skuId;
    }

    public String categoryId() {
        return categoryId;
    }

    public Money unitPrice() {
        return unitPrice;
    }

    public int quantity() {
        return quantity;
    }

    /** 行项小计 = 单价 × 数量。 */
    public Money amount() {
        return unitPrice.multiply(quantity);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CartItem other)) {
            return false;
        }
        return quantity == other.quantity
                && skuId.equals(other.skuId)
                && categoryId.equals(other.categoryId)
                && unitPrice.equals(other.unitPrice);
    }

    @Override
    public int hashCode() {
        return Objects.hash(skuId, categoryId, unitPrice, quantity);
    }

    @Override
    public String toString() {
        return skuId + "(" + categoryId + ") x" + quantity + " @ " + unitPrice;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}

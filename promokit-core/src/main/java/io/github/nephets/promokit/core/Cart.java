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

/** 购物车：租户、用户、会员等级、渠道与行项列表，不可变。 */
public final class Cart {

    private final String tenantId;
    private final String userId;
    private final int memberLevel;
    private final String channel;
    private final String currency;
    private final List<CartItem> items;

    /**
     * @param tenantId 租户 id
     * @param userId 用户 id
     * @param memberLevel 会员等级，0 表示普通
     * @param channel 下单渠道，如 APP、H5
     * @param currency 币种
     * @param items 行项，null 视为空车
     */
    public Cart(
            String tenantId,
            String userId,
            int memberLevel,
            String channel,
            String currency,
            List<CartItem> items) {
        this.tenantId = requireText(tenantId, "tenantId");
        this.userId = requireText(userId, "userId");
        this.memberLevel = memberLevel;
        this.channel = channel == null ? "DEFAULT" : channel;
        this.currency = requireText(currency, "currency");
        List<CartItem> copy = new ArrayList<>();
        if (items != null) {
            for (CartItem item : items) {
                copy.add(Objects.requireNonNull(item, "cart item must not be null"));
            }
        }
        this.items = Collections.unmodifiableList(copy);
    }

    /**
     * 便捷构造：币种取首个行项的币种，空车时为 CNY。
     *
     * @param tenantId 租户 id
     * @param userId 用户 id
     * @param memberLevel 会员等级
     * @param channel 下单渠道
     * @param items 行项
     */
    public static Cart of(
            String tenantId, String userId, int memberLevel, String channel, List<CartItem> items) {
        String currency = "CNY";
        if (items != null && !items.isEmpty()) {
            currency = items.get(0).unitPrice().currency();
        }
        return new Cart(tenantId, userId, memberLevel, channel, currency, items);
    }

    public String tenantId() {
        return tenantId;
    }

    public String userId() {
        return userId;
    }

    public int memberLevel() {
        return memberLevel;
    }

    public String channel() {
        return channel;
    }

    public String currency() {
        return currency;
    }

    public List<CartItem> items() {
        return items;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * 订单原价 = 各行项小计之和。
     *
     * @throws CurrencyMismatchException 行项币种与购物车币种不一致
     */
    public Money subtotal() {
        Money total = Money.zero(currency);
        for (CartItem item : items) {
            total = total.add(item.amount());
        }
        return total;
    }

    @Override
    public String toString() {
        return "Cart{tenant=" + tenantId + ", user=" + userId + ", items=" + items.size() + "}";
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}

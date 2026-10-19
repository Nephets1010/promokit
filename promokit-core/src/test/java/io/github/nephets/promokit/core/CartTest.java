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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CartTest {

    private static final Money PRICE = Money.of("30.00", "CNY");

    private static Cart cart(List<CartItem> items) {
        return Cart.of("acme", "u1", 1, "APP", items);
    }

    @Test
    void subtotalSumsItemAmounts() {
        List<CartItem> items = new ArrayList<>();
        items.add(new CartItem("sku-1", "book", PRICE, 2));
        items.add(new CartItem("sku-2", "toy", Money.of("15.50", "CNY"), 1));
        assertThat(cart(items).subtotal()).isEqualTo(Money.of("75.50", "CNY"));
    }

    @Test
    void emptyCartSubtotalIsZero() {
        Cart empty = cart(new ArrayList<>());
        assertThat(empty.isEmpty()).isTrue();
        assertThat(empty.subtotal()).isEqualTo(Money.of("0.00", "CNY"));
        assertThat(empty.currency()).isEqualTo("CNY");
        List<CartItem> usdItems = new ArrayList<>();
        usdItems.add(new CartItem("sku-1", "book", Money.of("10.00", "USD"), 1));
        assertThat(cart(usdItems).currency()).isEqualTo("USD");
    }

    @Test
    void itemListIsImmutable() {
        List<CartItem> items = new ArrayList<>();
        items.add(new CartItem("sku-1", "book", PRICE, 1));
        Cart cart = cart(items);
        items.clear();
        assertThat(cart.items()).hasSize(1);
        assertThatThrownBy(() -> cart.items().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void subtotalRejectsMixedCurrency() {
        List<CartItem> items = new ArrayList<>();
        items.add(new CartItem("sku-1", "book", PRICE, 1));
        items.add(new CartItem("sku-2", "toy", Money.of("1.00", "USD"), 1));
        assertThatThrownBy(() -> cart(items).subtotal())
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void channelDefaultsWhenNull() {
        Cart cart = new Cart("acme", "u1", 0, null, "CNY", List.of());
        assertThat(cart.channel()).isEqualTo("DEFAULT");
        assertThat(cart.memberLevel()).isZero();
        assertThatThrownBy(() -> new Cart(" ", "u1", 0, "APP", "CNY", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Cart("acme", null, 0, "APP", "CNY", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Cart("acme", "u1", 0, "APP", " ", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void itemAmountAndValidation() {
        CartItem item = CartItem.of("sku-1", "book", PRICE, 3);
        assertThat(item.amount()).isEqualTo(Money.of("90.00", "CNY"));
        assertThat(item).hasToString("sku-1(book) x3 @ 30.00 CNY");
        assertThat(item).isEqualTo(CartItem.of("sku-1", "book", PRICE, 3));
        assertThat(item.hashCode()).isEqualTo(CartItem.of("sku-1", "book", PRICE, 3).hashCode());
        assertThat(item).isNotEqualTo(CartItem.of("sku-1", "book", PRICE, 1));
    }

    @Test
    void itemRejectsBadInput() {
        assertThatThrownBy(() -> new CartItem("sku-1", "book", PRICE, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CartItem(" ", "book", PRICE, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CartItem("sku-1", " ", PRICE, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CartItem("sku-1", "book", null, 1))
                .isInstanceOf(NullPointerException.class);
    }
}

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
package io.github.nephets.promokit.core.condition;

import io.github.nephets.promokit.core.CartItem;
import io.github.nephets.promokit.core.Condition;
import io.github.nephets.promokit.core.PromotionContext;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** 指定商品：购物车里包含任一指定 sku。 */
public final class SkuInCondition implements Condition {

    private final Set<String> skuIds;

    public SkuInCondition(Set<String> skuIds) {
        Objects.requireNonNull(skuIds, "skuIds must not be null");
        this.skuIds = Collections.unmodifiableSet(new LinkedHashSet<>(skuIds));
    }

    public static SkuInCondition of(String... skuIds) {
        Objects.requireNonNull(skuIds, "skuIds must not be null");
        return new SkuInCondition(new LinkedHashSet<>(java.util.Arrays.asList(skuIds)));
    }

    public Set<String> skuIds() {
        return skuIds;
    }

    @Override
    public boolean matches(PromotionContext ctx) {
        for (CartItem item : ctx.cart().items()) {
            if (skuIds.contains(item.skuId())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String describe() {
        return "指定商品 " + String.join(",", skuIds);
    }

    @Override
    public String toString() {
        return describe();
    }
}

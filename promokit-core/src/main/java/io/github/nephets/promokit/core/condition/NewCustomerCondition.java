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

import io.github.nephets.promokit.core.Condition;
import io.github.nephets.promokit.core.PromotionContext;

/** 新客：上下文的新客标记必须与期望值一致，默认要求是新客。 */
public final class NewCustomerCondition implements Condition {

    private final boolean expected;

    public NewCustomerCondition() {
        this(true);
    }

    public NewCustomerCondition(boolean expected) {
        this.expected = expected;
    }

    public boolean expected() {
        return expected;
    }

    @Override
    public boolean matches(PromotionContext ctx) {
        return ctx.newCustomer() == expected;
    }

    @Override
    public String describe() {
        return expected ? "仅限新客" : "仅限老客";
    }

    @Override
    public String toString() {
        return describe();
    }
}

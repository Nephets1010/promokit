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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** 或：任一子条件命中即算命中，空列表视为不命中。 */
public final class OrCondition implements Condition {

    private final List<Condition> conditions;

    public OrCondition(List<Condition> conditions) {
        Objects.requireNonNull(conditions, "conditions must not be null");
        this.conditions = Collections.unmodifiableList(new ArrayList<>(conditions));
    }

    public static OrCondition of(Condition... conditions) {
        Objects.requireNonNull(conditions, "conditions must not be null");
        return new OrCondition(Arrays.asList(conditions));
    }

    public List<Condition> conditions() {
        return conditions;
    }

    @Override
    public boolean matches(PromotionContext ctx) {
        for (Condition condition : conditions) {
            if (condition.matches(ctx)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String describe() {
        return conditions.stream().map(Condition::describe).collect(Collectors.joining(" 或 "));
    }

    @Override
    public String toString() {
        return "(" + describe() + ")";
    }
}

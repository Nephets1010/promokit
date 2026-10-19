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

/** 会员等级：上下文里的会员等级不低于要求等级。 */
public final class MemberLevelCondition implements Condition {

    private final int minLevel;

    public MemberLevelCondition(int minLevel) {
        this.minLevel = minLevel;
    }

    public int minLevel() {
        return minLevel;
    }

    @Override
    public boolean matches(PromotionContext ctx) {
        return ctx.memberLevel() >= minLevel;
    }

    @Override
    public String describe() {
        return "会员等级 >= " + minLevel;
    }

    @Override
    public String toString() {
        return describe();
    }
}

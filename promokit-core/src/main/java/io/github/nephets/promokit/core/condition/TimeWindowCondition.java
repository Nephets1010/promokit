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

import java.time.Instant;
import java.util.Objects;

/** 时间窗：上下文的当前时间必须落在 [start, end) 内，用于活动窗口之外的附加时段限制。 */
public final class TimeWindowCondition implements Condition {

    private final Instant start;
    private final Instant end;

    public TimeWindowCondition(Instant start, Instant end) {
        this.start = Objects.requireNonNull(start, "start must not be null");
        this.end = Objects.requireNonNull(end, "end must not be null");
    }

    public Instant start() {
        return start;
    }

    public Instant end() {
        return end;
    }

    @Override
    public boolean matches(PromotionContext ctx) {
        Instant now = ctx.now();
        return !now.isBefore(start) && now.isBefore(end);
    }

    @Override
    public String describe() {
        return "时间在 " + start + " 至 " + end + " 之间";
    }

    @Override
    public String toString() {
        return describe();
    }
}

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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 促销活动：门槛条件 + 优惠策略 + 生效窗口 + 叠加与名额设置。
 *
 * <p>用 {@link #builder()} 构造。{@code stackable=false} 表示该活动只能单独使用，不能和其他活动一起叠加。
 */
public final class Promotion {

    private final String promotionId;
    private final String tenantId;
    private final String name;
    private final PromotionType type;
    private final List<Condition> conditions;
    private final DiscountStrategy strategy;
    private final Instant startTime;
    private final Instant endTime;
    private final PromotionStatus status;
    private final int priority;
    private final boolean stackable;
    private final String exclusionGroup;
    private final Integer totalQuota;
    private final Integer perUserQuota;

    private Promotion(Builder builder) {
        this.promotionId = builder.promotionId;
        this.tenantId = builder.tenantId;
        this.name = builder.name;
        this.type = builder.type;
        this.conditions = Collections.unmodifiableList(new ArrayList<>(builder.conditions));
        this.strategy = builder.strategy;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.status = builder.status;
        this.priority = builder.priority;
        this.stackable = builder.stackable;
        this.exclusionGroup = builder.exclusionGroup;
        this.totalQuota = builder.totalQuota;
        this.perUserQuota = builder.perUserQuota;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String promotionId() {
        return promotionId;
    }

    public String tenantId() {
        return tenantId;
    }

    public String name() {
        return name;
    }

    public PromotionType type() {
        return type;
    }

    public List<Condition> conditions() {
        return conditions;
    }

    public DiscountStrategy strategy() {
        return strategy;
    }

    public Instant startTime() {
        return startTime;
    }

    public Instant endTime() {
        return endTime;
    }

    public PromotionStatus status() {
        return status;
    }

    public int priority() {
        return priority;
    }

    public boolean stackable() {
        return stackable;
    }

    /** 互斥组名，null 或空串表示不参与互斥。 */
    public String exclusionGroup() {
        return exclusionGroup;
    }

    /** 总名额，null 表示不限。 */
    public Integer totalQuota() {
        return totalQuota;
    }

    /** 每人限用次数，null 表示不限。 */
    public Integer perUserQuota() {
        return perUserQuota;
    }

    /** 是否限定名额。 */
    public boolean hasQuota() {
        return totalQuota != null || perUserQuota != null;
    }

    /** 当前时间是否落在 [startTime, endTime) 内。 */
    public boolean isWithinWindow(Instant now) {
        return !now.isBefore(startTime) && now.isBefore(endTime);
    }

    /** 状态为 ACTIVE 且时间在窗口内。 */
    public boolean isActiveAt(Instant now) {
        return status == PromotionStatus.ACTIVE && isWithinWindow(now);
    }

    /** 所有门槛条件是否都命中。 */
    public boolean conditionsMatch(PromotionContext ctx) {
        return firstUnmatchedCondition(ctx).isEmpty();
    }

    /** 返回第一个未命中的条件，全部命中时返回空。 */
    public Optional<Condition> firstUnmatchedCondition(PromotionContext ctx) {
        for (Condition condition : conditions) {
            if (!condition.matches(ctx)) {
                return Optional.of(condition);
            }
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        return "Promotion{" + promotionId + ", " + name + ", " + type + ", " + status + "}";
    }

    /** {@link Promotion} 构造器。 */
    public static final class Builder {

        private String promotionId;
        private String tenantId;
        private String name;
        private PromotionType type;
        private final List<Condition> conditions = new ArrayList<>();
        private DiscountStrategy strategy;
        private Instant startTime;
        private Instant endTime;
        private PromotionStatus status = PromotionStatus.ACTIVE;
        private int priority = 0;
        private boolean stackable = true;
        private String exclusionGroup;
        private Integer totalQuota;
        private Integer perUserQuota;

        public Builder promotionId(String promotionId) {
            this.promotionId = promotionId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder type(PromotionType type) {
            this.type = type;
            return this;
        }

        public Builder conditions(List<Condition> conditions) {
            if (conditions != null) {
                this.conditions.addAll(conditions);
            }
            return this;
        }

        public Builder condition(Condition condition) {
            if (condition != null) {
                this.conditions.add(condition);
            }
            return this;
        }

        public Builder strategy(DiscountStrategy strategy) {
            this.strategy = strategy;
            return this;
        }

        /** 生效窗口，同时设置开始与结束时间。 */
        public Builder window(Instant startTime, Instant endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
            return this;
        }

        public Builder startTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(Instant endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder status(PromotionStatus status) {
            this.status = status;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder stackable(boolean stackable) {
            this.stackable = stackable;
            return this;
        }

        public Builder exclusionGroup(String exclusionGroup) {
            this.exclusionGroup = exclusionGroup;
            return this;
        }

        public Builder totalQuota(Integer totalQuota) {
            this.totalQuota = totalQuota;
            return this;
        }

        public Builder perUserQuota(Integer perUserQuota) {
            this.perUserQuota = perUserQuota;
            return this;
        }

        public Promotion build() {
            Objects.requireNonNull(promotionId, "promotionId must not be null");
            Objects.requireNonNull(tenantId, "tenantId must not be null");
            Objects.requireNonNull(name, "name must not be null");
            Objects.requireNonNull(type, "type must not be null");
            Objects.requireNonNull(strategy, "strategy must not be null");
            Objects.requireNonNull(startTime, "startTime must not be null");
            Objects.requireNonNull(endTime, "endTime must not be null");
            Objects.requireNonNull(status, "status must not be null");
            if (!endTime.isAfter(startTime)) {
                throw new IllegalArgumentException("endTime must be after startTime");
            }
            if (totalQuota != null && totalQuota < 0) {
                throw new IllegalArgumentException("totalQuota must not be negative");
            }
            if (perUserQuota != null && perUserQuota < 0) {
                throw new IllegalArgumentException("perUserQuota must not be negative");
            }
            return new Promotion(this);
        }
    }
}

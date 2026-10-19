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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 算价上下文：一次算价请求所需的全部输入。
 *
 * <p>租户、用户、会员等级、渠道缺省时取自 {@link Cart}；时间缺省取 {@link Instant#now()}。
 */
public final class PromotionContext {

    private final String tenantId;
    private final String userId;
    private final int memberLevel;
    private final String channel;
    private final Cart cart;
    private final Instant now;
    private final boolean newCustomer;
    private final Map<String, Object> attributes;

    private PromotionContext(Builder builder) {
        this.tenantId = builder.tenantId != null ? builder.tenantId : builder.cart.tenantId();
        this.userId = builder.userId != null ? builder.userId : builder.cart.userId();
        this.memberLevel = builder.memberLevel;
        this.channel = builder.channel != null ? builder.channel : builder.cart.channel();
        this.cart = builder.cart;
        this.now = builder.now;
        this.newCustomer = builder.newCustomer;
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(builder.attributes));
    }

    /** 以购物车为基础创建构造器。 */
    public static Builder builder(Cart cart) {
        return new Builder(cart);
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

    public Cart cart() {
        return cart;
    }

    public Instant now() {
        return now;
    }

    public boolean newCustomer() {
        return newCustomer;
    }

    public Map<String, Object> attributes() {
        return attributes;
    }

    /** 读取扩展属性，不存在返回默认值。 */
    public Object attribute(String key, Object defaultValue) {
        Object value = attributes.get(key);
        return value == null ? defaultValue : value;
    }

    /** 上下文构造器。 */
    public static final class Builder {

        private final Cart cart;
        private String tenantId;
        private String userId;
        private int memberLevel = 0;
        private String channel;
        private Instant now = Instant.now();
        private boolean newCustomer = false;
        private final Map<String, Object> attributes = new LinkedHashMap<>();

        private Builder(Cart cart) {
            this.cart = Objects.requireNonNull(cart, "cart must not be null");
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder memberLevel(int memberLevel) {
            this.memberLevel = memberLevel;
            return this;
        }

        public Builder channel(String channel) {
            this.channel = channel;
            return this;
        }

        public Builder now(Instant now) {
            this.now = Objects.requireNonNull(now, "now must not be null");
            return this;
        }

        public Builder newCustomer(boolean newCustomer) {
            this.newCustomer = newCustomer;
            return this;
        }

        public Builder attribute(String key, Object value) {
            this.attributes.put(key, value);
            return this;
        }

        public Builder attributes(Map<String, Object> attributes) {
            if (attributes != null) {
                this.attributes.putAll(attributes);
            }
            return this;
        }

        public PromotionContext build() {
            return new PromotionContext(this);
        }
    }
}

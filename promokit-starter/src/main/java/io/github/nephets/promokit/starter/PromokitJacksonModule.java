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
package io.github.nephets.promokit.starter;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.module.SimpleModule;

import io.github.nephets.promokit.core.Discount;
import io.github.nephets.promokit.core.Money;
import io.github.nephets.promokit.core.PriceResult;
import io.github.nephets.promokit.core.coupon.Coupon;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Jackson 模块：core 的值对象用 {@code 字段名()} 取值，没有 {@code getXxx} 前缀，Jackson 默认认不出来， 这里用 mixin 把访问器标成 JSON
 * 属性，让 {@link PriceResult} 和 {@link Coupon} 能直接作为接口返回值序列化。
 */
public class PromokitJacksonModule extends SimpleModule {

    private static final long serialVersionUID = 1L;

    public PromokitJacksonModule() {
        super("promokit");
        setMixInAnnotation(Money.class, MoneyMixin.class);
        setMixInAnnotation(Discount.class, DiscountMixin.class);
        setMixInAnnotation(PriceResult.class, PriceResultMixin.class);
        setMixInAnnotation(Coupon.class, CouponMixin.class);
    }

    abstract static class MoneyMixin {

        @JsonProperty("amount")
        abstract BigDecimal amount();

        @JsonProperty("currency")
        abstract String currency();
    }

    abstract static class DiscountMixin {

        @JsonProperty("promotionId")
        abstract String promotionId();

        @JsonProperty("amount")
        abstract Money amount();

        @JsonProperty("description")
        abstract String description();
    }

    abstract static class PriceResultMixin {

        @JsonProperty("subtotal")
        abstract Money subtotal();

        @JsonProperty("totalDiscount")
        abstract Money totalDiscount();

        @JsonProperty("payable")
        abstract Money payable();

        @JsonProperty("applied")
        abstract List<Discount> applied();

        @JsonProperty("rejectedReasons")
        abstract List<String> rejectedReasons();
    }

    abstract static class CouponMixin {

        @JsonProperty("code")
        abstract String code();

        @JsonProperty("templateId")
        abstract String templateId();

        @JsonProperty("tenantId")
        abstract String tenantId();

        @JsonProperty("ownerId")
        abstract String ownerId();

        @JsonProperty("faceValue")
        abstract Money faceValue();

        @JsonProperty("threshold")
        abstract Money threshold();

        @JsonProperty("expireAt")
        abstract Instant expireAt();

        @JsonProperty("status")
        abstract String status();
    }
}

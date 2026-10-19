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
package io.github.nephets.promokit.sample;

import io.github.nephets.promokit.core.Money;
import io.github.nephets.promokit.core.Promotion;
import io.github.nephets.promokit.core.PromotionRegistry;
import io.github.nephets.promokit.core.PromotionType;
import io.github.nephets.promokit.core.condition.MinAmountCondition;
import io.github.nephets.promokit.core.coupon.CouponService;
import io.github.nephets.promokit.core.coupon.CouponTemplate;
import io.github.nephets.promokit.core.strategy.DiscountRateStrategy;
import io.github.nephets.promokit.core.strategy.FullReductionStrategy;
import io.github.nephets.promokit.core.strategy.TieredStrategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/** 示例活动与券模板：租户 acme 下三个活动（满减 / 折扣 / 阶梯），满减与折扣同属互斥组 base。 */
@Configuration(proxyBeanMethods = false)
public class SamplePromotions {

    private static final Logger log = LoggerFactory.getLogger(SamplePromotions.class);

    /** 示例租户。 */
    public static final String TENANT = "acme";

    /** 券模板 id。 */
    public static final String COUPON_TEMPLATE = "newcomer";

    private static final Money CNY_100 = Money.of("100.00", "CNY");
    private static final Money CNY_150 = Money.of("150.00", "CNY");
    private static final Money CNY_200 = Money.of("200.00", "CNY");

    public SamplePromotions(PromotionRegistry registry, CouponService couponService) {
        Instant start = Instant.now().minus(Duration.ofDays(1));
        Instant end = Instant.now().plus(Duration.ofDays(30));

        registry.register(
                Promotion.builder()
                        .promotionId("full-100-20")
                        .tenantId(TENANT)
                        .name("满100减20")
                        .type(PromotionType.FULL_REDUCTION)
                        .strategy(new FullReductionStrategy(CNY_100, Money.of("20.00", "CNY")))
                        .window(start, end)
                        .exclusionGroup("base")
                        .priority(1)
                        .build());

        registry.register(
                Promotion.builder()
                        .promotionId("discount-90")
                        .tenantId(TENANT)
                        .name("全场9折")
                        .type(PromotionType.DISCOUNT)
                        .strategy(new DiscountRateStrategy(new BigDecimal("0.90")))
                        .window(start, end)
                        .exclusionGroup("base")
                        .priority(2)
                        .build());

        registry.register(
                Promotion.builder()
                        .promotionId("tiered")
                        .tenantId(TENANT)
                        .name("阶梯满减")
                        .type(PromotionType.TIERED)
                        .strategy(
                                TieredStrategy.of(
                                        TieredStrategy.Tier.of(CNY_150, Money.of("25.00", "CNY")),
                                        TieredStrategy.Tier.of(CNY_200, Money.of("40.00", "CNY"))))
                        .condition(new MinAmountCondition(CNY_150))
                        .window(start, end)
                        .priority(3)
                        .build());

        couponService.registerTemplate(
                new CouponTemplate(
                        COUPON_TEMPLATE,
                        TENANT,
                        "新人券",
                        Money.of("15.00", "CNY"),
                        CNY_100,
                        start,
                        end,
                        10));
        log.info(
                "sample data registered tenant={} promotions={} couponTemplate={}",
                TENANT,
                3,
                COUPON_TEMPLATE);
    }
}

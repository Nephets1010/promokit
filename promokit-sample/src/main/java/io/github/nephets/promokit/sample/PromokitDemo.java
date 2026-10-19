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

import io.github.nephets.promokit.core.Cart;
import io.github.nephets.promokit.core.CartItem;
import io.github.nephets.promokit.core.CouponAlreadyUsedException;
import io.github.nephets.promokit.core.InMemoryQuotaStore;
import io.github.nephets.promokit.core.LlmClient;
import io.github.nephets.promokit.core.Money;
import io.github.nephets.promokit.core.PriceEngine;
import io.github.nephets.promokit.core.PriceResult;
import io.github.nephets.promokit.core.Promotion;
import io.github.nephets.promokit.core.PromotionContext;
import io.github.nephets.promokit.core.PromotionRegistry;
import io.github.nephets.promokit.core.PromotionType;
import io.github.nephets.promokit.core.SuggestedPromotion;
import io.github.nephets.promokit.core.coupon.Coupon;
import io.github.nephets.promokit.core.coupon.CouponService;
import io.github.nephets.promokit.core.strategy.FlashSaleStrategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 演示流程：算价明细 → 建议 → 券发放核销（含重复核销）→ 名额用尽。 */
@Component
public class PromokitDemo {

    private static final Logger log = LoggerFactory.getLogger(PromokitDemo.class);

    private final PriceEngine priceEngine;
    private final PromotionRegistry registry;
    private final CouponService couponService;
    private final InMemoryQuotaStore quotaStore;
    private final LlmClient llmClient;

    public PromokitDemo(
            PriceEngine priceEngine,
            PromotionRegistry registry,
            CouponService couponService,
            InMemoryQuotaStore quotaStore,
            LlmClient llmClient) {
        this.priceEngine = priceEngine;
        this.registry = registry;
        this.couponService = couponService;
        this.quotaStore = quotaStore;
        this.llmClient = llmClient;
    }

    /** 构建示例购物车：一本书、两件玩具、一袋零食。 */
    public Cart sampleCart() {
        List<CartItem> items = new ArrayList<>();
        items.add(new CartItem("sku-book", "book", Money.of("68.00", "CNY"), 1));
        items.add(new CartItem("sku-toy", "toy", Money.of("45.00", "CNY"), 2));
        items.add(new CartItem("sku-snack", "food", Money.of("19.90", "CNY"), 1));
        return Cart.of(SamplePromotions.TENANT, "u-1001", 2, "APP", items);
    }

    /** 跑一遍完整演示。 */
    public void run() {
        Cart cart = sampleCart();
        PromotionContext ctx =
                PromotionContext.builder(cart)
                        .tenantId(SamplePromotions.TENANT)
                        .newCustomer(true)
                        .now(Instant.now())
                        .build();
        List<Promotion> promotions = registry.byTenant(SamplePromotions.TENANT);

        log.info("== 购物车 ==");
        for (CartItem item : cart.items()) {
            log.info("  {} x{} 小计 {}", item.skuId(), item.quantity(), item.amount());
        }
        log.info("  原价 {}", cart.subtotal());
        log.info("  参与活动 {} 个", promotions.size());

        PriceResult result = priceEngine.quote(promotions, ctx);
        printResult(result);
        printSuggestions(cart);
        demoCoupon(ctx);
        demoQuota(ctx);
    }

    /** 打印算价明细。 */
    public void printResult(PriceResult result) {
        log.info("== 算价明细 ==");
        for (io.github.nephets.promokit.core.Discount discount : result.applied()) {
            log.info(
                    "  {}：{}，减 {}",
                    discount.promotionId(),
                    discount.description(),
                    discount.amount());
        }
        for (String reason : result.rejectedReasons()) {
            log.info("  未生效：{}", reason);
        }
        log.info("  优惠合计 {}，应付 {}", result.totalDiscount(), result.payable());
    }

    private void printSuggestions(Cart cart) {
        log.info("== 活动建议 ==");
        for (SuggestedPromotion suggestion : llmClient.suggest(cart, "清库存")) {
            log.info("  {}", suggestion);
        }
    }

    private void demoCoupon(PromotionContext ctx) {
        log.info("== 券发放与核销 ==");
        Coupon coupon = couponService.issue(SamplePromotions.COUPON_TEMPLATE, "u-1001");
        log.info("  发放 {}，状态 {}", coupon.code(), coupon.status());
        Coupon used = couponService.redeem(coupon.code(), ctx);
        log.info("  首次核销后状态 {}", used.status());
        try {
            couponService.redeem(coupon.code(), ctx);
        } catch (CouponAlreadyUsedException ex) {
            log.info("  重复核销被拒：{}", ex.getMessage());
        }
        log.info(
                "  幂等核销返回状态 {}，isRedeemed={}",
                couponService.redeemIfAbsent(coupon.code(), ctx).orElseThrow().status(),
                couponService.isRedeemed(coupon.code()));
    }

    private void demoQuota(PromotionContext ctx) {
        log.info("== 名额用尽 ==");
        Promotion flashSale =
                Promotion.builder()
                        .promotionId("flash-sale")
                        .tenantId(SamplePromotions.TENANT)
                        .name("秒杀直降")
                        .type(PromotionType.FLASH_SALE)
                        .strategy(
                                new FlashSaleStrategy(Map.of("sku-toy", Money.of("30.00", "CNY"))))
                        .window(
                                Instant.now().minus(Duration.ofMinutes(1)),
                                Instant.now().plus(Duration.ofHours(1)))
                        .totalQuota(1)
                        .perUserQuota(1)
                        .build();
        quotaStore.registerQuota(flashSale);
        PriceResult first = priceEngine.quote(List.of(flashSale), ctx);
        log.info("  第一次算价：优惠 {}，应付 {}", first.totalDiscount(), first.payable());
        PriceResult second = priceEngine.quote(List.of(flashSale), ctx);
        log.info("  第二次算价：优惠 {}，应付 {}", second.totalDiscount(), second.payable());
        for (String reason : second.rejectedReasons()) {
            log.info("  未生效：{}", reason);
        }
    }
}

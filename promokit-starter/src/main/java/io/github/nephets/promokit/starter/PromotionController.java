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

import io.github.nephets.promokit.core.Cart;
import io.github.nephets.promokit.core.CartItem;
import io.github.nephets.promokit.core.CouponNotFoundException;
import io.github.nephets.promokit.core.Money;
import io.github.nephets.promokit.core.PriceEngine;
import io.github.nephets.promokit.core.PriceResult;
import io.github.nephets.promokit.core.PromokitTenant;
import io.github.nephets.promokit.core.PromotionContext;
import io.github.nephets.promokit.core.PromotionRegistry;
import io.github.nephets.promokit.core.coupon.Coupon;
import io.github.nephets.promokit.core.coupon.CouponService;
import io.github.nephets.promokit.core.coupon.CouponStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 促销接口：算价、发券、核销。
 *
 * <p>租户优先取请求体里的 tenantId，其次取 {@link PromokitTenant}（由 {@code X-Tenant-Id} 请求头写入）。
 */
@RestController
public class PromotionController {

    /** 请求与线程上下文都没有租户时的兜底值，也是 {@code promokit.default-tenant-id} 的默认值。 */
    public static final String DEFAULT_TENANT_ID = "default";

    private final PriceEngine priceEngine;
    private final PromotionRegistry registry;
    private final CouponService couponService;
    private final PromokitProperties properties;

    private static final Logger log = LoggerFactory.getLogger(PromotionController.class);

    public PromotionController(
            PriceEngine priceEngine,
            PromotionRegistry registry,
            CouponService couponService,
            PromokitProperties properties) {
        this.priceEngine = priceEngine;
        this.registry = registry;
        this.couponService = couponService;
        this.properties = properties;
    }

    /** 算价：返回原价、优惠明细与应付。 */
    @PostMapping("/promokit/quote")
    public PriceResult quote(@RequestBody QuoteRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String tenantId = resolveTenant(request.getTenantId());
        log.info(
                "quote request tenantId={} userId={} items={}",
                tenantId,
                request.getUserId(),
                request.getItems() == null ? 0 : request.getItems().size());
        Cart cart = toCart(tenantId, request);
        PromotionContext ctx =
                PromotionContext.builder(cart)
                        .tenantId(tenantId)
                        .userId(request.getUserId() == null ? cart.userId() : request.getUserId())
                        .memberLevel(
                                request.getMemberLevel() == null ? 0 : request.getMemberLevel())
                        .channel(request.getChannel())
                        .newCustomer(Boolean.TRUE.equals(request.getNewCustomer()))
                        .now(Instant.now())
                        .build();
        List<io.github.nephets.promokit.core.Promotion> promotions = registry.byTenant(tenantId);
        try {
            return priceEngine.quote(promotions, ctx);
        } catch (RuntimeException ex) {
            // 原样抛出让上层决定响应码，这里只负责把请求上下文打全，避免线上只剩一个 500 无据可查。
            log.error(
                    "quote failed tenantId={} userId={} items={}",
                    tenantId,
                    ctx.userId(),
                    ctx.cart().items().size(),
                    ex);
            throw ex;
        }
    }

    /** 发券：按模板发一张券给用户，模板发完返回 409 语义的错误由异常处理器决定。 */
    @PostMapping("/promokit/coupons/issue")
    public Coupon issue(
            @RequestParam("templateId") String templateId, @RequestParam("userId") String userId) {
        log.info("issue coupon templateId={} userId={}", templateId, userId);
        try {
            return couponService.issue(templateId, userId);
        } catch (RuntimeException ex) {
            log.error("issue coupon failed templateId={} userId={}", templateId, userId, ex);
            throw ex;
        }
    }

    /** 核销券：幂等，重复提交同一券码不会重复扣减。 */
    @PostMapping("/promokit/coupons/redeem")
    public RedeemResponse redeem(@RequestBody RedeemRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String code = request.getCode();
        log.info(
                "redeem request code={} tenantId={} userId={}",
                code,
                request.getTenantId(),
                request.getUserId());
        Optional<Coupon> existing = couponService.find(code);
        if (existing.isEmpty()) {
            return RedeemResponse.notFound(code);
        }
        Coupon before = existing.get();
        String tenantId = resolveTenant(request.getTenantId());
        PromotionContext ctx = singleItemContext(tenantId, request);
        try {
            Optional<Coupon> redeemed = couponService.redeemIfAbsent(code, ctx);
            if (redeemed.isEmpty()) {
                return RedeemResponse.notFound(code);
            }
            Coupon after = redeemed.get();
            if (before.status() == CouponStatus.USED) {
                return RedeemResponse.alreadyUsed(code, after.status().name());
            }
            return RedeemResponse.redeemed(code, after.status().name());
        } catch (CouponNotFoundException ex) {
            log.error("redeem failed code={} not found", code, ex);
            return RedeemResponse.notFound(code);
        } catch (RuntimeException ex) {
            log.error("redeem failed code={} statusBefore={}", code, before.status().name(), ex);
            return RedeemResponse.rejected(code, before.status().name(), ex.getMessage());
        }
    }

    private PromotionContext singleItemContext(String tenantId, RedeemRequest request) {
        BigDecimal amount = request.getAmount() == null ? BigDecimal.ZERO : request.getAmount();
        String currency = firstNonBlank(request.getCurrency(), properties.getDefaultCurrency());
        List<CartItem> items = new ArrayList<>();
        items.add(new CartItem("COUPON-ORDER", "ALL", Money.of(amount, currency), 1));
        Cart cart =
                new Cart(
                        tenantId,
                        request.getUserId() == null ? "anonymous" : request.getUserId(),
                        0,
                        "DEFAULT",
                        currency,
                        items);
        return PromotionContext.builder(cart).tenantId(tenantId).now(Instant.now()).build();
    }

    private Cart toCart(String tenantId, QuoteRequest request) {
        String currency = firstNonBlank(request.getCurrency(), properties.getDefaultCurrency());
        List<CartItem> items = new ArrayList<>();
        if (request.getItems() != null) {
            for (QuoteRequest.ItemRequest item : request.getItems()) {
                BigDecimal unitPrice =
                        item.getUnitPrice() == null ? BigDecimal.ZERO : item.getUnitPrice();
                int quantity = item.getQuantity() == null ? 1 : item.getQuantity();
                items.add(
                        new CartItem(
                                item.getSkuId(),
                                item.getCategoryId(),
                                Money.of(unitPrice, currency),
                                quantity));
            }
        }
        return new Cart(
                tenantId,
                request.getUserId() == null ? "anonymous" : request.getUserId(),
                0,
                request.getChannel() == null ? "DEFAULT" : request.getChannel(),
                currency,
                items);
    }

    private String resolveTenant(String requested) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        String current = PromokitTenant.current();
        if (current != null) {
            return current;
        }
        String fallback = properties.getDefaultTenantId();
        log.debug("tenant not provided, fallback to {} from promokit.default-tenant-id", fallback);
        return fallback;
    }

    private static String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}

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

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.nephets.promokit.core.InMemoryQuotaStore;
import io.github.nephets.promokit.core.Money;
import io.github.nephets.promokit.core.PriceEngine;
import io.github.nephets.promokit.core.Promotion;
import io.github.nephets.promokit.core.PromotionRegistry;
import io.github.nephets.promokit.core.PromotionType;
import io.github.nephets.promokit.core.coupon.CouponService;
import io.github.nephets.promokit.core.coupon.CouponTemplate;
import io.github.nephets.promokit.core.strategy.FullReductionStrategy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PromotionControllerTest {

    private static final Instant START = Instant.now().minusSeconds(3600);
    private static final Instant END = Instant.now().plusSeconds(3600);

    private PromotionRegistry registry;
    private CouponService couponService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        registry = new PromotionRegistry();
        registry.register(
                Promotion.builder()
                        .promotionId("p1")
                        .tenantId("acme")
                        .name("满100减20")
                        .type(PromotionType.FULL_REDUCTION)
                        .strategy(
                                new FullReductionStrategy(
                                        Money.of("100.00", "CNY"), Money.of("20.00", "CNY")))
                        .window(START, END)
                        .build());
        couponService = new CouponService();
        couponService.registerTemplate(
                new CouponTemplate(
                        "t1",
                        "acme",
                        "新人券",
                        Money.of("15.00", "CNY"),
                        Money.of("100.00", "CNY"),
                        START,
                        END,
                        5));
        PromokitProperties properties = new PromokitProperties();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new PromokitJacksonModule());
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        PromotionController controller =
                new PromotionController(
                        new PriceEngine(new InMemoryQuotaStore(), 3),
                        registry,
                        couponService,
                        properties);
        mockMvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                        .build();
    }

    private static String quoteBody(String tenantId, String unitPrice, int quantity) {
        return """
                {"tenantId":"%s","userId":"u1","currency":"CNY","channel":"APP","items":[
                  {"skuId":"sku-1","categoryId":"book","unitPrice":%s,"quantity":%d}]}
                """
                .formatted(tenantId, unitPrice, quantity);
    }

    @Test
    void quoteAppliesRegisteredPromotion() throws Exception {
        mockMvc.perform(
                        post("/promokit/quote")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(quoteBody("acme", "60.00", 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal.amount").value(120.00))
                .andExpect(jsonPath("$.totalDiscount.amount").value(20.00))
                .andExpect(jsonPath("$.payable.amount").value(100.00))
                .andExpect(jsonPath("$.applied[0].promotionId").value("p1"));
    }

    @Test
    void quoteWithoutRegisteredPromotionReturnsSubtotal() throws Exception {
        mockMvc.perform(
                        post("/promokit/quote")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(quoteBody("globex", "60.00", 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal.amount").value(60.00))
                .andExpect(jsonPath("$.totalDiscount.amount").value(0.00))
                .andExpect(jsonPath("$.applied.length()").value(0));

        mockMvc.perform(
                        post("/promokit/quote")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(quoteBody("", "60.00", 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payable.amount").value(60.00));
    }

    @Test
    void issueReturnsUnusedCoupon() throws Exception {
        mockMvc.perform(
                        post("/promokit/coupons/issue")
                                .param("templateId", "t1")
                                .param("userId", "u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNUSED"))
                .andExpect(jsonPath("$.ownerId").value("u1"))
                .andExpect(jsonPath("$.faceValue.amount").value(15.00));
    }

    @Test
    void redeemMarksCouponUsed() throws Exception {
        String code = couponService.issue("t1", "u1").code();
        mockMvc.perform(
                        post("/promokit/coupons/redeem")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(redeemBody(code, "150.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redeemed").value(true))
                .andExpect(jsonPath("$.status").value("USED"))
                .andExpect(jsonPath("$.reason").value("核销成功"));
    }

    @Test
    void redeemTwiceIsIdempotent() throws Exception {
        String code = couponService.issue("t1", "u1").code();
        mockMvc.perform(
                        post("/promokit/coupons/redeem")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(redeemBody(code, "150.00")))
                .andExpect(status().isOk());
        mockMvc.perform(
                        post("/promokit/coupons/redeem")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(redeemBody(code, "150.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redeemed").value(true))
                .andExpect(
                        jsonPath("$.reason").value(org.hamcrest.Matchers.containsString("之前已核销")));
        assertThat(couponService.issuedCount("t1")).isEqualTo(1);
    }

    @Test
    void redeemUnknownCodeIsNotFound() throws Exception {
        mockMvc.perform(
                        post("/promokit/coupons/redeem")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(redeemBody("CPN-nope", "150.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redeemed").value(false))
                .andExpect(jsonPath("$.status").value("NOT_FOUND"));
    }

    @Test
    void redeemBelowThresholdIsRejected() throws Exception {
        String code = couponService.issue("t1", "u1").code();
        mockMvc.perform(
                        post("/promokit/coupons/redeem")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(redeemBody(code, "50.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redeemed").value(false))
                .andExpect(jsonPath("$.status").value("UNUSED"))
                .andExpect(jsonPath("$.reason").value(org.hamcrest.Matchers.containsString("门槛")));
    }

    private static String redeemBody(String code, String amount) {
        return """
                {"code":"%s","tenantId":"acme","userId":"u1","amount":%s,"currency":"CNY"}
                """
                .formatted(code, amount);
    }

    @Test
    void quoteFallsBackToConfiguredDefaultTenant() throws Exception {
        PromokitProperties properties = new PromokitProperties();
        properties.setDefaultTenantId("acme");
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new PromokitJacksonModule());
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        PromotionController controller =
                new PromotionController(
                        new PriceEngine(new InMemoryQuotaStore(), 3),
                        registry,
                        couponService,
                        properties);
        MockMvc local =
                MockMvcBuilders.standaloneSetup(controller)
                        .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                        .build();
        // 请求体不带 tenantId、线程上下文也没有租户，这时反查配置的 promokit.default-tenant-id。
        local.perform(
                        post("/promokit/quote")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(quoteBody("", "60.00", 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDiscount.amount").value(20.00))
                .andExpect(jsonPath("$.applied[0].promotionId").value("p1"));
    }
}

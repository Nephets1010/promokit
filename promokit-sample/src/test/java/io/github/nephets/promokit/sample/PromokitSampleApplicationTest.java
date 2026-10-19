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
import io.github.nephets.promokit.core.PriceResult;
import io.github.nephets.promokit.core.PromotionContext;
import io.github.nephets.promokit.core.PromotionRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PromokitSampleApplicationTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private PromotionRegistry registry;

    @Autowired private PromokitDemo demo;

    @Test
    void contextLoadsWithSamplePromotions() {
        assertThat(registry.byTenant(SamplePromotions.TENANT)).hasSize(3);
        assertThat(registry.active(SamplePromotions.TENANT, java.time.Instant.now())).hasSize(3);
    }

    @Test
    void quoteEndpointReturnsDiscount() throws Exception {
        mockMvc.perform(
                        post("/promokit/quote")
                                .header("X-Tenant-Id", SamplePromotions.TENANT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"userId":"u-1001","currency":"CNY","channel":"APP","items":[
                                          {"skuId":"sku-book","categoryId":"book","unitPrice":68.00,"quantity":1},
                                          {"skuId":"sku-toy","categoryId":"toy","unitPrice":45.00,"quantity":2},
                                          {"skuId":"sku-snack","categoryId":"food","unitPrice":19.90,"quantity":1}]}
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal.amount").value(177.90))
                .andExpect(jsonPath("$.totalDiscount.amount").value(45.00))
                .andExpect(jsonPath("$.payable.amount").value(132.90))
                .andExpect(jsonPath("$.applied.length()").value(2))
                .andExpect(jsonPath("$.rejectedReasons.length()").value(1));
    }

    @Test
    void demoRunsAndProducesPriceResult() {
        io.github.nephets.promokit.core.Cart cart = demo.sampleCart();
        PromotionContext ctx =
                PromotionContext.builder(cart)
                        .tenantId(SamplePromotions.TENANT)
                        .now(java.time.Instant.now())
                        .build();
        demo.run();
        PriceResult result =
                new io.github.nephets.promokit.core.PriceEngine()
                        .quote(registry.byTenant(SamplePromotions.TENANT), ctx);
        assertThat(result.subtotal()).isEqualTo(Money.of("177.90", "CNY"));
        assertThat(result.payable()).isEqualTo(result.subtotal().subtract(result.totalDiscount()));
        assertThat(result.payable().isNegative()).isFalse();
    }
}

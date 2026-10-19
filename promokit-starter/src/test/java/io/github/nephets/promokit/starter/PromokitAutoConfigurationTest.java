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

import io.github.nephets.promokit.core.InMemoryQuotaStore;
import io.github.nephets.promokit.core.LlmClient;
import io.github.nephets.promokit.core.PriceEngine;
import io.github.nephets.promokit.core.PromotionRegistry;
import io.github.nephets.promokit.core.coupon.CouponService;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class PromokitAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(PromokitAutoConfiguration.class));

    private final WebApplicationContextRunner webRunner =
            new WebApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(PromokitAutoConfiguration.class));

    @Test
    void coreBeansAreRegistered() {
        runner.run(
                context -> {
                    assertThat(context).hasSingleBean(PromokitProperties.class);
                    assertThat(context).hasSingleBean(InMemoryQuotaStore.class);
                    assertThat(context).hasSingleBean(PriceEngine.class);
                    assertThat(context).hasSingleBean(CouponService.class);
                    assertThat(context).hasSingleBean(PromotionRegistry.class);
                    assertThat(context).hasSingleBean(LlmClient.class);
                    assertThat(context).hasSingleBean(PromokitTenantInterceptor.class);
                    assertThat(context).hasSingleBean(PromokitWebMvcConfigurer.class);
                    assertThat(context.getBean(LlmClient.class))
                            .isInstanceOf(RuleBasedLlmClient.class);
                });
    }

    @Test
    void maxStackDepthIsAppliedToEngine() {
        runner.withPropertyValues("promokit.max-stack-depth=5")
                .run(
                        context ->
                                assertThat(context.getBean(PriceEngine.class).maxStackDepth())
                                        .isEqualTo(5));
    }

    @Test
    void userBeansWinOverAutoConfiguration() {
        runner.withUserConfiguration(CustomBeans.class)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(PromotionRegistry.class);
                            assertThat(context.getBean(PromotionRegistry.class))
                                    .isSameAs(CustomBeans.REGISTRY);
                            assertThat(context).hasSingleBean(LlmClient.class);
                            assertThat(context.getBean(LlmClient.class))
                                    .isSameAs(CustomBeans.CLIENT);
                        });
    }

    @Test
    void tenantHeaderIsConfigurable() {
        runner.withPropertyValues("promokit.tenant-header=X-Shop-Id")
                .run(
                        context ->
                                assertThat(
                                                context.getBean(PromokitTenantInterceptor.class)
                                                        .headerName())
                                        .isEqualTo("X-Shop-Id"));
    }

    @Test
    void webControllerAbsentWithoutServletWebApplication() {
        // PromotionController 只在 servlet web 应用下装配（@ConditionalOnWebApplication +
        // @ConditionalOnClass）。
        runner.run(context -> assertThat(context).doesNotHaveBean(PromotionController.class));
    }

    @Test
    void webControllerPresentInServletWebApplication() {
        webRunner.run(
                context -> {
                    assertThat(context).hasSingleBean(PromotionController.class);
                    assertThat(context).hasSingleBean(PromokitJacksonModule.class);
                });
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    static class CustomBeans {

        static final PromotionRegistry REGISTRY = new PromotionRegistry();

        static final LlmClient CLIENT = (cart, goal) -> java.util.List.of();

        @org.springframework.context.annotation.Bean
        PromotionRegistry customRegistry() {
            return REGISTRY;
        }

        @org.springframework.context.annotation.Bean
        LlmClient customLlmClient() {
            return CLIENT;
        }
    }
}

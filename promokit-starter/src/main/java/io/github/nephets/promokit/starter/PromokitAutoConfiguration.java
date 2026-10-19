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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * promokit 自动装配：注册名额存储、算价引擎、券服务、活动注册表与本地活动建议实现。
 *
 * <p>所有 Bean 都带 {@code @ConditionalOnMissingBean}，应用自定义实现即可覆盖。Web 接口只在 servlet 环境下装配。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PromokitProperties.class)
public class PromokitAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PromokitAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public InMemoryQuotaStore quotaStore(PromokitProperties properties) {
        log.debug(
                "auto-configuring default InMemoryQuotaStore capacity={}",
                properties.getQuotaCapacity());
        return new InMemoryQuotaStore(properties.getQuotaCapacity());
    }

    @Bean
    @ConditionalOnMissingBean
    public PriceEngine priceEngine(InMemoryQuotaStore quotaStore, PromokitProperties properties) {
        log.debug("auto-configuring PriceEngine maxStackDepth={}", properties.getMaxStackDepth());
        return new PriceEngine(quotaStore, properties.getMaxStackDepth());
    }

    @Bean
    @ConditionalOnMissingBean
    public CouponService couponService(PromokitProperties properties) {
        log.debug(
                "auto-configuring CouponService maxTemplates={} maxCoupons={}",
                properties.getMaxTemplates(),
                properties.getMaxCoupons());
        return new CouponService(
                Clock.systemUTC(), properties.getMaxTemplates(), properties.getMaxCoupons());
    }

    @Bean
    @ConditionalOnMissingBean
    public PromotionRegistry promotionRegistry(PromokitProperties properties) {
        log.debug(
                "auto-configuring PromotionRegistry capacity={}", properties.getRegistryCapacity());
        return new PromotionRegistry(properties.getRegistryCapacity());
    }

    @Bean
    @ConditionalOnMissingBean
    public LlmClient llmClient() {
        return new RuleBasedLlmClient();
    }

    @Bean
    @ConditionalOnMissingBean(PromokitJacksonModule.class)
    public PromokitJacksonModule promokitJacksonModule() {
        return new PromokitJacksonModule();
    }

    @Bean
    @ConditionalOnMissingBean
    public PromokitTenantInterceptor promokitTenantInterceptor(PromokitProperties properties) {
        log.debug(
                "auto-configuring PromokitTenantInterceptor tenantHeader={}"
                        + " defaultTenantId={}",
                properties.getTenantHeader(),
                properties.getDefaultTenantId());
        return new PromokitTenantInterceptor(properties.getTenantHeader());
    }

    @Bean
    @ConditionalOnMissingBean
    public PromokitWebMvcConfigurer promokitWebMvcConfigurer(
            PromokitTenantInterceptor interceptor) {
        return new PromokitWebMvcConfigurer(interceptor);
    }

    /** Web 相关 Bean：classpath 有 DispatcherServlet 且是 servlet 应用时才装配。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.web.servlet.DispatcherServlet")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public static class PromotionWebConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public PromotionController promotionController(
                PriceEngine priceEngine,
                PromotionRegistry registry,
                CouponService couponService,
                PromokitProperties properties) {
            return new PromotionController(priceEngine, registry, couponService, properties);
        }
    }
}

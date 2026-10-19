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

import io.github.nephets.promokit.core.PromokitTenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import static org.assertj.core.api.Assertions.assertThat;

class PromokitTenantInterceptorTest {

    @AfterEach
    void tearDown() {
        PromokitTenant.clear();
    }

    @Test
    void headerIsWrittenIntoContext() {
        PromokitTenantInterceptor interceptor = new PromokitTenantInterceptor("X-Tenant-Id");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", " acme ");
        assertThat(interceptor.preHandle(request, null, new Object())).isTrue();
        assertThat(PromokitTenant.current()).isEqualTo("acme");
        interceptor.afterCompletion(request, null, new Object(), null);
        assertThat(PromokitTenant.current()).isNull();
    }

    @Test
    void missingHeaderLeavesContextUntouched() {
        PromokitTenantInterceptor interceptor = new PromokitTenantInterceptor(null);
        assertThat(interceptor.headerName()).isEqualTo("X-Tenant-Id");
        interceptor.preHandle(new MockHttpServletRequest(), null, new Object());
        assertThat(PromokitTenant.isSet()).isFalse();
    }

    @Test
    void configurerRegistersInterceptorForAllPaths() {
        PromokitTenantInterceptor interceptor = new PromokitTenantInterceptor("X-Shop-Id");
        PromokitWebMvcConfigurer configurer = new PromokitWebMvcConfigurer(interceptor);
        assertThat(configurer.interceptor()).isSameAs(interceptor);
        RecordingRegistry registry = new RecordingRegistry();
        configurer.addInterceptors(registry);
        assertThat(registry.invoked).isTrue();
    }

    private static class RecordingRegistry extends InterceptorRegistry {

        private boolean invoked = false;

        @Override
        public org.springframework.web.servlet.config.annotation.InterceptorRegistration
                addInterceptor(org.springframework.web.servlet.HandlerInterceptor interceptor) {
            invoked = true;
            return super.addInterceptor(interceptor);
        }
    }
}

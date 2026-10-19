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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 按请求头写入租户上下文，请求结束后清理，避免线程复用时串租户。 */
public class PromokitTenantInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(PromokitTenantInterceptor.class);

    private final String headerName;

    public PromokitTenantInterceptor(String headerName) {
        this.headerName =
                headerName == null || headerName.isBlank()
                        ? PromokitProperties.DEFAULT_TENANT_HEADER
                        : headerName;
    }

    public String headerName() {
        return headerName;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        String tenantId = request.getHeader(headerName);
        if (tenantId == null || tenantId.isBlank()) {
            log.debug("tenant header {} missing or blank, context left untouched", headerName);
            return true;
        }
        PromokitTenant.set(tenantId.trim());
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {
        PromokitTenant.clear();
    }
}

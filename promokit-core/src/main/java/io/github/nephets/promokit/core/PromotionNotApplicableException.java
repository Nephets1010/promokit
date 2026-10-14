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
package io.github.nephets.promokit.core;

/** 活动不适用：门槛未达到、租户不匹配或上下文缺少租户。 */
public class PromotionNotApplicableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PromotionNotApplicableException(String message) {
        super(message);
    }

    public PromotionNotApplicableException(String message, Throwable cause) {
        super(message, cause);
    }
}

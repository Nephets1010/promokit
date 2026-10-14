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

/** 活动或券已过期。 */
public class PromotionExpiredException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String targetId;

    public PromotionExpiredException(String targetId) {
        super("活动或券 " + targetId + " 已过期");
        this.targetId = targetId;
    }

    public PromotionExpiredException(String targetId, Throwable cause) {
        super("活动或券 " + targetId + " 已过期", cause);
        this.targetId = targetId;
    }

    public String targetId() {
        return targetId;
    }
}

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

/** 名额已用尽：发券超过模板总量或活动名额被抢完。 */
public class QuotaExhaustedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public QuotaExhaustedException(String message) {
        super(message);
    }

    public QuotaExhaustedException(String message, Throwable cause) {
        super(message, cause);
    }
}

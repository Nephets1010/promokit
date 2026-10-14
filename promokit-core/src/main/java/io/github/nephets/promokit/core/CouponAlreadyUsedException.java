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

/** 券已核销：同一张券重复核销时抛出。 */
public class CouponAlreadyUsedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;

    public CouponAlreadyUsedException(String code) {
        super("券 " + code + " 已核销");
        this.code = code;
    }

    public CouponAlreadyUsedException(String code, Throwable cause) {
        super("券 " + code + " 已核销", cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}

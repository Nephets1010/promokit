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

/** 币种不一致：两个金额做加减或比较时币种不同。 */
public class CurrencyMismatchException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String expected;
    private final String actual;

    public CurrencyMismatchException(String expected, String actual) {
        super("币种不一致：期望 " + expected + "，实际 " + actual);
        this.expected = expected;
        this.actual = actual;
    }

    public CurrencyMismatchException(String expected, String actual, Throwable cause) {
        super("币种不一致：期望 " + expected + "，实际 " + actual, cause);
        this.expected = expected;
        this.actual = actual;
    }

    public String expected() {
        return expected;
    }

    public String actual() {
        return actual;
    }
}

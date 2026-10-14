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

/** 核销结果。重复核销同一张券时 redeemed 仍为 true，reason 说明是本次核销还是之前已核销。 */
public record RedeemResponse(String code, String status, boolean redeemed, String reason) {

    /** 券不存在。 */
    public static RedeemResponse notFound(String code) {
        return new RedeemResponse(code, "NOT_FOUND", false, "券 " + code + " 不存在");
    }

    /** 本次完成核销。 */
    public static RedeemResponse redeemed(String code, String status) {
        return new RedeemResponse(code, status, true, "核销成功");
    }

    /** 之前已核销，本次不重复扣减。 */
    public static RedeemResponse alreadyUsed(String code, String status) {
        return new RedeemResponse(code, status, true, "券 " + code + " 之前已核销，本次不重复核销");
    }

    /** 核销被拒，如已过期、租户不匹配或未达门槛。 */
    public static RedeemResponse rejected(String code, String status, String reason) {
        return new RedeemResponse(code, status, false, reason);
    }
}

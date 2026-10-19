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

/** 活动类型。 */
public enum PromotionType {
    /** 满减：订单满额减固定金额。 */
    FULL_REDUCTION,
    /** 折扣：按折扣率打折。 */
    DISCOUNT,
    /** 阶梯满减：按订单金额所在档位减。 */
    TIERED,
    /** 券：指定面额与门槛。 */
    COUPON,
    /** 秒杀：指定商品直降到秒杀价。 */
    FLASH_SALE
}

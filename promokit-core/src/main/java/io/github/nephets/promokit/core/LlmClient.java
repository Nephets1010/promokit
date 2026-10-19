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

import java.util.List;

/** 活动建议接口。core 只定义契约，不绑定任何大模型 SDK；starter 里用本地规则实现，接入外部模型时替换实现即可。 */
public interface LlmClient {

    /**
     * 按购物车和目标给出活动建议。
     *
     * @param cart 购物车
     * @param goal 运营目标，如「提升客单价」「清库存」
     * @return 建议列表，按推荐顺序排列
     */
    List<SuggestedPromotion> suggest(Cart cart, String goal);
}

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

/**
 * 门槛条件：判断活动在给定上下文下是否可用。
 *
 * <p>{@link #describe()} 用于算价结果里说明未命中原因，默认返回实现类的简单类名。
 */
public interface Condition {

    /**
     * @param ctx 算价上下文
     * @return true 表示命中该条件
     */
    boolean matches(PromotionContext ctx);

    /** 条件的人话描述，写进算价的拒绝原因里。 */
    default String describe() {
        return getClass().getSimpleName();
    }
}

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

/** 活动状态。只有 {@link #ACTIVE} 且当前时间在活动窗口内才会参与算价。 */
public enum PromotionStatus {
    /** 草稿，不参与算价。 */
    DRAFT,
    /** 生效中。 */
    ACTIVE,
    /** 已暂停，不参与算价。 */
    PAUSED,
    /** 已结束，不参与算价。 */
    ENDED
}

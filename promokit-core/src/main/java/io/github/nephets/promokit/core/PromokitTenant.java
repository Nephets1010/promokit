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

import java.util.concurrent.Callable;

/**
 * 租户上下文：基于 {@link InheritableThreadLocal} 的当前租户持有器。
 *
 * <p>{@link #runWith} / {@link #callWith} 在任务结束（含异常）后恢复原上下文，避免线程池串租户。
 */
public final class PromokitTenant {

    private static final ThreadLocal<String> CURRENT = new InheritableThreadLocal<>();

    private PromokitTenant() {}

    /** 设置当前线程的租户 id（覆盖已有值）。 */
    public static void set(String tenantId) {
        CURRENT.set(tenantId);
    }

    /** 返回当前线程的租户 id；未设置时为 null。 */
    public static String current() {
        return CURRENT.get();
    }

    /** 当前线程是否已设置租户。 */
    public static boolean isSet() {
        return CURRENT.get() != null;
    }

    /** 清除当前线程的租户。 */
    public static void clear() {
        CURRENT.remove();
    }

    /**
     * 返回当前租户 id，未设置时抛异常。
     *
     * @throws PromotionNotApplicableException 当前线程没有租户
     */
    public static String require() {
        String tenantId = CURRENT.get();
        if (tenantId == null) {
            throw new PromotionNotApplicableException("当前线程未设置租户，无法完成算价");
        }
        return tenantId;
    }

    /** 在指定租户上下文中执行 {@link Runnable}，结束后恢复原上下文。 */
    public static void runWith(String tenantId, Runnable task) {
        String previous = CURRENT.get();
        CURRENT.set(tenantId);
        try {
            task.run();
        } finally {
            restore(previous);
        }
    }

    /** 在指定租户上下文中执行 {@link Callable} 并返回结果，结束后恢复原上下文。 */
    public static <T> T callWith(String tenantId, Callable<T> task) throws Exception {
        String previous = CURRENT.get();
        CURRENT.set(tenantId);
        try {
            return task.call();
        } finally {
            restore(previous);
        }
    }

    private static void restore(String previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }
}

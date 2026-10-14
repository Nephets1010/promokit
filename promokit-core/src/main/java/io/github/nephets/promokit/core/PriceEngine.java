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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 算价引擎。
 *
 * <p>一次 {@link #quote} 做四件事：
 *
 * <ol>
 *   <li>过滤：状态 ACTIVE、当前时间在活动窗口内、租户匹配、所有门槛条件命中，且策略算出的优惠大于 0；
 *   <li>解决冲突：同一互斥组内只留优惠最大的（相等取 priority 小的）；不可叠加活动只能单独使用，与可叠加组合比总优惠后取大者；
 *   <li>名额：逐个预留，预留失败则回滚本轮已预留的额度并剔除该活动；
 *   <li>汇总：算出优惠合计与应付，优惠合计超过原价时按原价截断，保证应付不为负。
 * </ol>
 */
public final class PriceEngine {

    /** 默认最多叠加的活动数。 */
    public static final int DEFAULT_MAX_STACK_DEPTH = 3;

    /** 优惠从优到劣：金额降序 → priority 升序 → 活动 id 升序。 */
    private static final Comparator<Candidate> BEST_FIRST =
            Comparator.<Candidate, Money>comparing(c -> c.discount.amount())
                    .reversed()
                    .thenComparingInt(c -> c.promotion.priority())
                    .thenComparing(c -> c.promotion.promotionId());

    private final QuotaStore quotaStore;
    private final int maxStackDepth;

    private static final Logger log = LoggerFactory.getLogger(PriceEngine.class);

    /** 不限名额、最多叠加 3 个活动。 */
    public PriceEngine() {
        this(null, DEFAULT_MAX_STACK_DEPTH);
    }

    /** 指定名额存储，最多叠加 3 个活动。 */
    public PriceEngine(QuotaStore quotaStore) {
        this(quotaStore, DEFAULT_MAX_STACK_DEPTH);
    }

    /**
     * @param quotaStore 名额存储，null 表示不做名额控制
     * @param maxStackDepth 最多叠加的活动数，必须 >= 1
     */
    public PriceEngine(QuotaStore quotaStore, int maxStackDepth) {
        this.quotaStore = quotaStore;
        if (maxStackDepth < 1) {
            throw new IllegalArgumentException("maxStackDepth must be >= 1, got " + maxStackDepth);
        }
        this.maxStackDepth = maxStackDepth;
    }

    public QuotaStore quotaStore() {
        return quotaStore;
    }

    public int maxStackDepth() {
        return maxStackDepth;
    }

    /**
     * 算价。
     *
     * @param promotions 候选活动
     * @param ctx 算价上下文
     * @return 算价结果，含生效明细与被剔除原因
     */
    public PriceResult quote(List<Promotion> promotions, PromotionContext ctx) {
        Objects.requireNonNull(promotions, "promotions must not be null");
        Objects.requireNonNull(ctx, "ctx must not be null");
        log.info(
                "quote tenant={} userId={} currency={} promotionCount={}",
                ctx.tenantId(),
                ctx.userId(),
                ctx.cart().currency(),
                promotions.size());

        List<String> rejected = new ArrayList<>();
        List<Candidate> eligible = filterEligible(promotions, ctx, rejected);
        List<Candidate> resolved = resolveExclusionGroups(eligible, rejected);
        List<Candidate> chosen = selectCombination(resolved, ctx);
        List<Candidate> reserved = reserveQuota(chosen, ctx, rejected);
        PriceResult result = buildResult(ctx, reserved, rejected);
        log.info(
                "quote done tenant={} userId={} subtotal={} totalDiscount={} payable={}"
                        + " applied={} rejected={}",
                ctx.tenantId(),
                ctx.userId(),
                result.subtotal(),
                result.totalDiscount(),
                result.payable(),
                result.applied().size(),
                result.rejectedReasons().size());
        return result;
    }

    private List<Candidate> filterEligible(
            List<Promotion> promotions, PromotionContext ctx, List<String> rejected) {
        List<Candidate> eligible = new ArrayList<>();
        for (Promotion promotion : promotions) {
            if (promotion == null) {
                rejected.add("活动列表含 null，已跳过");
                continue;
            }
            if (!promotion.tenantId().equals(ctx.tenantId())) {
                rejected.add(reason(promotion, "不属于租户 " + ctx.tenantId()));
                continue;
            }
            if (promotion.status() != PromotionStatus.ACTIVE) {
                rejected.add(reason(promotion, "状态为 " + promotion.status()));
                continue;
            }
            if (ctx.now().isBefore(promotion.startTime())) {
                rejected.add(reason(promotion, "活动未开始"));
                continue;
            }
            if (!ctx.now().isBefore(promotion.endTime())) {
                rejected.add(reason(promotion, "活动已结束"));
                continue;
            }
            Optional<Condition> unmatched = promotion.firstUnmatchedCondition(ctx);
            if (unmatched.isPresent()) {
                rejected.add(reason(promotion, "未满足门槛：" + unmatched.get().describe()));
                continue;
            }
            Discount discount = promotion.strategy().compute(promotion, ctx);
            if (!discount.amount().isPositive()) {
                rejected.add(reason(promotion, "优惠金额为 0"));
                continue;
            }
            eligible.add(new Candidate(promotion, discount));
        }
        return eligible;
    }

    private List<Candidate> resolveExclusionGroups(
            List<Candidate> eligible, List<String> rejected) {
        Map<String, List<Candidate>> groups = new LinkedHashMap<>();
        List<Candidate> ungrouped = new ArrayList<>();
        for (Candidate candidate : eligible) {
            String group = candidate.promotion.exclusionGroup();
            if (group == null || group.isBlank()) {
                ungrouped.add(candidate);
            } else {
                groups.computeIfAbsent(group, key -> new ArrayList<>()).add(candidate);
            }
        }
        List<Candidate> resolved = new ArrayList<>(ungrouped);
        for (Map.Entry<String, List<Candidate>> entry : groups.entrySet()) {
            List<Candidate> members = new ArrayList<>(entry.getValue());
            members.sort(BEST_FIRST);
            Candidate winner = members.get(0);
            resolved.add(winner);
            for (int i = 1; i < members.size(); i++) {
                rejected.add(
                        reason(
                                members.get(i).promotion,
                                "互斥组 ["
                                        + entry.getKey()
                                        + "] 已选用优惠更大的活动 "
                                        + winner.promotion.promotionId()));
            }
        }
        resolved.sort(BEST_FIRST);
        return resolved;
    }

    private List<Candidate> selectCombination(List<Candidate> resolved, PromotionContext ctx) {
        List<Candidate> stackable = new ArrayList<>();
        Candidate bestExclusive = null;
        for (Candidate candidate : resolved) {
            if (candidate.promotion.stackable()) {
                stackable.add(candidate);
            } else if (bestExclusive == null || BEST_FIRST.compare(candidate, bestExclusive) < 0) {
                bestExclusive = candidate;
            }
        }
        if (stackable.size() > maxStackDepth) {
            stackable = new ArrayList<>(stackable.subList(0, maxStackDepth));
        }
        Money zero = Money.zero(ctx.cart().currency());
        Money stackSum = zero;
        for (Candidate candidate : stackable) {
            stackSum = stackSum.add(candidate.discount.amount());
        }
        Money exclusiveSum = bestExclusive == null ? zero : bestExclusive.discount.amount();
        if (bestExclusive != null && exclusiveSum.compareTo(stackSum) >= 0) {
            List<Candidate> solo = new ArrayList<>();
            solo.add(bestExclusive);
            return solo;
        }
        return stackable;
    }

    private List<Candidate> reserveQuota(
            List<Candidate> chosen, PromotionContext ctx, List<String> rejected) {
        List<Candidate> reserved = new ArrayList<>();
        for (Candidate candidate : chosen) {
            Promotion promotion = candidate.promotion;
            if (quotaStore == null || !promotion.hasQuota()) {
                reserved.add(candidate);
                continue;
            }
            if (!quotaStore.reserve(ctx.tenantId(), promotion.promotionId(), ctx.userId())) {
                for (Candidate rollback : reserved) {
                    if (rollback.promotion.hasQuota()) {
                        quotaStore.release(
                                ctx.tenantId(), rollback.promotion.promotionId(), ctx.userId());
                    }
                }
                // 预留到一半失败必须回滚本轮已预留的额度，否则券/秒杀名额会被白占。
                log.warn(
                        "quota reserve failed tenant={} userId={} promotionId={} rolledBack={}",
                        ctx.tenantId(),
                        ctx.userId(),
                        promotion.promotionId(),
                        reserved.size());
                reserved.clear();
                rejected.add(reason(promotion, "名额不足"));
                continue;
            }
            reserved.add(candidate);
        }
        return reserved;
    }

    private PriceResult buildResult(
            PromotionContext ctx, List<Candidate> reserved, List<String> rejected) {
        Money subtotal = ctx.cart().subtotal();
        Money totalDiscount = Money.zero(subtotal.currency());
        List<Discount> applied = new ArrayList<>();
        for (Candidate candidate : reserved) {
            totalDiscount = totalDiscount.add(candidate.discount.amount());
            applied.add(candidate.discount);
        }
        if (totalDiscount.compareTo(subtotal) > 0) {
            // 优惠被截断说明活动叠加口径有问题（例如两个互斥组同时放行），必须留痕便于复盘资损。
            log.warn(
                    "discount exceeds subtotal tenant={} userId={} subtotal={} rawDiscount={},"
                            + " truncated to subtotal",
                    ctx.tenantId(),
                    ctx.userId(),
                    subtotal,
                    totalDiscount);
            rejected.add("优惠合计超过订单原价，已按原价截断");
            totalDiscount = subtotal;
        }
        Money payable = subtotal.subtract(totalDiscount);
        return new PriceResult(subtotal, totalDiscount, payable, applied, rejected);
    }

    private static String reason(Promotion promotion, String message) {
        return "活动 " + promotion.promotionId() + "(" + promotion.name() + ")：" + message;
    }

    /** 活动 + 本次算出的优惠，引擎内部使用。 */
    private static final class Candidate {

        private final Promotion promotion;
        private final Discount discount;

        Candidate(Promotion promotion, Discount discount) {
            this.promotion = promotion;
            this.discount = discount;
        }
    }
}

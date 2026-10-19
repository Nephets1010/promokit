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
package io.github.nephets.promokit.core.coupon;

import io.github.nephets.promokit.core.CouponAlreadyUsedException;
import io.github.nephets.promokit.core.CouponNotFoundException;
import io.github.nephets.promokit.core.PromotionContext;
import io.github.nephets.promokit.core.PromotionExpiredException;
import io.github.nephets.promokit.core.PromotionNotApplicableException;
import io.github.nephets.promokit.core.QuotaExhaustedException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 券服务：登记模板、发放、核销、作废。
 *
 * <p>发放按模板总量计数，超发抛 {@link QuotaExhaustedException}。核销是幂等的：重复核销同一张券会抛 {@link
 * CouponAlreadyUsedException}，需要「已核销就直接返回」语义时用 {@link #redeemIfAbsent}。
 *
 * <p>模板与券都按 LRU 有容量上限：券只发不归档，长期运行会无限增长成内存泄漏。淘汰券时优先挑已经进入终态 （USED /
 * EXPIRED）的，因为唯一会被「丢状态」影响的语义是重复核销保护；确实只剩下未核销券可淘汰时，打 WARN 说明 这条券失去了重复核销防护。
 */
public final class CouponService {

    /** 默认最多登记的券模板数。 */
    public static final int DEFAULT_MAX_TEMPLATES = 10_000;

    /** 默认最多保留的券实例数。 */
    public static final int DEFAULT_MAX_COUPONS = 200_000;

    private final Map<String, CouponTemplate> templates = new ConcurrentHashMap<>();
    private final Map<String, Coupon> coupons = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> issuedCounts = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private final Clock clock;
    private final int maxTemplates;
    private final int maxCoupons;

    /** 保护两张 LRU 顺序表：注册新的模板/券时必须持锁才能做溢出淘汰。 */
    private final Object templateLock = new Object();

    private final Object couponLock = new Object();

    private final LinkedHashMap<String, Boolean> templateRecency =
            new LinkedHashMap<>(64, 0.75f, true);
    private final LinkedHashMap<String, Boolean> couponRecency =
            new LinkedHashMap<>(64, 0.75f, true);

    private static final Logger log = LoggerFactory.getLogger(CouponService.class);

    /** 使用系统时钟，容量取默认值。 */
    public CouponService() {
        this(Clock.systemUTC());
    }

    /**
     * @param clock 时钟，用于判断过期
     */
    public CouponService(Clock clock) {
        this(clock, DEFAULT_MAX_TEMPLATES, DEFAULT_MAX_COUPONS);
    }

    /**
     * @param clock 时钟，用于判断过期
     * @param maxTemplates 最多登记的模板数，必须为正数
     * @param maxCoupons 最多保留的券实例数，必须为正数
     */
    public CouponService(Clock clock, int maxTemplates, int maxCoupons) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        if (maxTemplates < 1) {
            throw new IllegalArgumentException("maxTemplates must be >= 1, got " + maxTemplates);
        }
        if (maxCoupons < 1) {
            throw new IllegalArgumentException("maxCoupons must be >= 1, got " + maxCoupons);
        }
        this.maxTemplates = maxTemplates;
        this.maxCoupons = maxCoupons;
    }

    /** 登记券模板，同 id 覆盖。 */
    public void registerTemplate(CouponTemplate template) {
        Objects.requireNonNull(template, "template must not be null");
        String templateId = template.templateId();
        templates.put(templateId, template);
        touchTemplate(templateId);
        log.info(
                "register coupon template templateId={} tenantId={} totalQuantity={}",
                templateId,
                template.tenantId(),
                template.totalQuantity());
    }

    public Optional<CouponTemplate> template(String templateId) {
        return Optional.ofNullable(templates.get(templateId));
    }

    /**
     * 发券。
     *
     * @param templateId 模板 id
     * @param userId 归属用户
     * @return 新券
     * @throws CouponNotFoundException 模板未登记
     * @throws QuotaExhaustedException 已发放数量达到模板总量
     */
    public Coupon issue(String templateId, String userId) {
        Objects.requireNonNull(templateId, "templateId must not be null");
        CouponTemplate template = requireTemplate(templateId);
        touchTemplate(templateId);
        AtomicInteger counter =
                issuedCounts.computeIfAbsent(templateId, key -> new AtomicInteger());
        synchronized (counter) {
            if (counter.get() >= template.totalQuantity()) {
                // 发放失败是业务常态（券发完），但要能追到「谁在什么时候把这张模板发爆了」。
                log.warn(
                        "issue coupon rejected templateId={} userId={} issued={} totalQuantity={}",
                        templateId,
                        userId,
                        counter.get(),
                        template.totalQuantity());
                throw new QuotaExhaustedException(
                        "券模板 " + templateId + " 已发完，总量 " + template.totalQuantity());
            }
            counter.incrementAndGet();
        }
        String code = "CPN-" + templateId + "-" + String.format("%06d", sequence.incrementAndGet());
        Coupon coupon =
                new Coupon(
                        code,
                        template.templateId(),
                        template.tenantId(),
                        userId,
                        template.faceValue(),
                        template.threshold(),
                        template.endTime(),
                        CouponStatus.UNUSED);
        coupons.put(code, coupon);
        touchCoupon(code);
        log.info(
                "issue coupon templateId={} userId={} code={} remaining={}",
                templateId,
                userId,
                code,
                Math.max(0, template.totalQuantity() - counter.get()));
        return coupon;
    }

    /**
     * 核销。
     *
     * @param code 券码
     * @param ctx 算价上下文，用于校验租户与订单金额门槛
     * @return 核销后的券
     * @throws CouponNotFoundException 券码不存在
     * @throws CouponAlreadyUsedException 券已核销
     * @throws PromotionExpiredException 券已过期
     * @throws PromotionNotApplicableException 租户不匹配或订单未达门槛
     */
    public Coupon redeem(String code, PromotionContext ctx) {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(ctx, "ctx");
        log.info("redeem coupon code={} tenant={}", code, ctx.tenantId());
        // 用 ConcurrentHashMap.compute 把「读券→校验→置为已核销」整个临界区原子化：
        // 并发核销同一券码时，只有一个线程能成功把状态从 UNUSED 改为 USED，其余线程会因
        // 当前状态已不是 UNUSED 而抛 CouponAlreadyUsedException，从而避免同一券被重复折扣（资损）。
        return coupons.compute(
                code,
                (k, cur) -> {
                    if (cur == null) {
                        log.warn("redeem rejected code={} reason=not found", code);
                        throw new CouponNotFoundException(code);
                    }
                    if (cur.status() == CouponStatus.USED) {
                        log.warn("redeem rejected code={} reason=already used", code);
                        throw new CouponAlreadyUsedException(code);
                    }
                    Instant now = ctx.now();
                    if (cur.status() == CouponStatus.EXPIRED || !now.isBefore(cur.expireAt())) {
                        log.warn(
                                "redeem rejected code={} reason=expired at {}",
                                code,
                                cur.expireAt());
                        throw new PromotionExpiredException(code);
                    }
                    if (!cur.tenantId().equals(ctx.tenantId())) {
                        log.warn(
                                "redeem rejected code={} reason=tenant mismatch expected={} actual={}",
                                code,
                                cur.tenantId(),
                                ctx.tenantId());
                        throw new PromotionNotApplicableException(
                                "券 " + code + " 不属于租户 " + ctx.tenantId());
                    }
                    if (cur.threshold() != null
                            && ctx.cart().subtotal().compareTo(cur.threshold()) < 0) {
                        log.warn(
                                "redeem rejected code={} reason=below threshold threshold={} subtotal={}",
                                code,
                                cur.threshold(),
                                ctx.cart().subtotal());
                        throw new PromotionNotApplicableException(
                                "订单金额未达到券 " + code + " 的门槛 " + cur.threshold());
                    }
                    log.info("redeem coupon code={} statusBefore={}", code, cur.status());
                    return cur.withStatus(CouponStatus.USED);
                });
    }

    /**
     * 幂等核销：已核销或仍可用时返回券，已核销返回的是原券。
     *
     * @param code 券码
     * @param ctx 算价上下文
     * @return 核销后的券；券不存在时返回空
     */
    public Optional<Coupon> redeemIfAbsent(String code, PromotionContext ctx) {
        Coupon coupon = coupons.get(code);
        if (coupon == null) {
            return Optional.empty();
        }
        if (coupon.status() == CouponStatus.USED) {
            return Optional.of(coupon);
        }
        try {
            return Optional.of(redeem(code, ctx));
        } catch (CouponAlreadyUsedException e) {
            // 并发已被其他线程核销：按「已核销返回原券」语义返回当前券。
            log.debug("redeemIfAbsent served after concurrent redeem code={}", code, e);
            return Optional.of(requireCoupon(code));
        }
    }

    /** 券是否已核销。 */
    public boolean isRedeemed(String code) {
        Coupon coupon = coupons.get(code);
        return coupon != null && coupon.status() == CouponStatus.USED;
    }

    /**
     * 作废券。已核销的券不能再作废。
     *
     * @param code 券码
     * @throws CouponNotFoundException 券码不存在
     * @throws CouponAlreadyUsedException 券已核销
     */
    public void expire(String code) {
        Objects.requireNonNull(code, "code must not be null");
        log.info("expire coupon code={}", code);
        // 同样用 compute 原子化，避免与并发 redeem/expire 交叉导致状态错乱。
        coupons.compute(
                code,
                (k, cur) -> {
                    if (cur == null) {
                        log.warn("expire rejected code={} reason=not found", code);
                        throw new CouponNotFoundException(code);
                    }
                    if (cur.status() == CouponStatus.USED) {
                        log.warn("expire rejected code={} reason=already used", code);
                        throw new CouponAlreadyUsedException(code);
                    }
                    if (cur.status() == CouponStatus.EXPIRED) {
                        log.debug("expire skipped code={} reason=already expired", code);
                        return cur;
                    }
                    return cur.withStatus(CouponStatus.EXPIRED);
                });
    }

    /** 按券码查券。 */
    public Optional<Coupon> find(String code) {
        return Optional.ofNullable(coupons.get(code));
    }

    /** 某用户名下所有券。 */
    public List<Coupon> couponsOf(String userId) {
        List<Coupon> result = new ArrayList<>();
        for (Coupon coupon : coupons.values()) {
            if (Objects.equals(userId, coupon.ownerId())) {
                result.add(coupon);
            }
        }
        return result;
    }

    /** 模板已发放数量。 */
    public int issuedCount(String templateId) {
        AtomicInteger counter = issuedCounts.get(templateId);
        return counter == null ? 0 : counter.get();
    }

    /** 剩余可发放数量。 */
    public int remaining(String templateId) {
        CouponTemplate template = requireTemplate(templateId);
        return Math.max(0, template.totalQuantity() - issuedCount(templateId));
    }

    /** 当前时间，取自构造时传入的时钟。 */
    public Instant now() {
        return clock.instant();
    }

    private CouponTemplate requireTemplate(String templateId) {
        CouponTemplate template = templates.get(templateId);
        if (template == null) {
            throw new CouponNotFoundException(templateId);
        }
        return template;
    }

    /**
     * 标记模板为最近使用；溢出时连同发放计数淘汰最旧的一条。
     *
     * <p>为什么模板淘汰必须连带删除发放计数：发放计数（{@link #issuedCounts}）是「这个模板已经发 了多少张」的唯一账本，只删模板不删计数的话，同一个模板 id
     * 重新登记后会继承旧计数，直接少发 一批券；而删了模板却留着计数更说不通。淘汰后该模板的 {@link #issue} 会抛 {@link
     * CouponNotFoundException}，语义降级为「未登记」，调用方按新模板重新登记即可恢复。
     */
    private void touchTemplate(String templateId) {
        synchronized (templateLock) {
            templateRecency.put(templateId, Boolean.TRUE);
            while (templateRecency.size() > maxTemplates) {
                String eldest = templateRecency.keySet().iterator().next();
                templateRecency.remove(eldest);
                templates.remove(eldest);
                issuedCounts.remove(eldest);
                log.warn(
                        "coupon template evicted templateId={} reason=capacity {} exceeded, "
                                + "issued count reset",
                        eldest,
                        maxTemplates);
            }
        }
    }

    /** 标记券为最近使用；溢出时优先淘汰已进入终态的券。 */
    private void touchCoupon(String code) {
        synchronized (couponLock) {
            couponRecency.put(code, Boolean.TRUE);
            while (couponRecency.size() > maxCoupons) {
                evictOneCoupon();
            }
        }
    }

    /**
     * 淘汰一张券。优先挑已核销或已作废的——它们已经没有「再次核销」的可能，丢掉不影响资金安全；只有当全部券都是 未核销状态时才会动它们，并打 WARN 报警。
     *
     * <p>为什么优先淘汰终态券：唯一会被「丢状态」影响的语义是重复核销保护——券记录没了，同一张券
     * 物理上就还能再核销一次。终态券（USED/EXPIRED）已经不可能进入核销路径，淘汰它们零资损风险； 未核销券一旦被淘汰就失去这层保护，所以只在没有终态券可淘汰时才动，并打 WARN
     * 说明这条券失去了 重复核销防护。
     *
     * <p>必须在 {@code couponLock} 保护下调用。
     */
    private void evictOneCoupon() {
        String eldest = null;
        for (String code : couponRecency.keySet()) {
            Coupon coupon = coupons.get(code);
            if (coupon != null && coupon.status() != CouponStatus.UNUSED) {
                eldest = code;
                break;
            }
        }
        if (eldest == null) {
            eldest = couponRecency.keySet().iterator().next();
            log.warn(
                    "coupon evicted code={} reason=capacity {} exceeded, only UNUSED coupons left: "
                            + "repeat-redeem protection lost for this coupon",
                    eldest,
                    maxCoupons);
        } else {
            log.debug("coupon evicted code={} reason=capacity {} exceeded", eldest, maxCoupons);
        }
        couponRecency.remove(eldest);
        coupons.remove(eldest);
    }

    private Coupon requireCoupon(String code) {
        Coupon coupon = coupons.get(code);
        if (coupon == null) {
            throw new CouponNotFoundException(code);
        }
        return coupon;
    }
}

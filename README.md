# promokit

促销规则与算价引擎：定义满减 / 折扣 / 阶梯 / 券 / 秒杀活动，按门槛条件筛选可用活动，再按互斥组与叠加规则算出最终价和优惠明细；名额（总名额、每人限用）走预留-确认-回滚，避免超发。core 不依赖 Spring，starter 提供 Spring Boot 自动装配。

## 模块

| 模块 | 说明 |
| --- | --- |
| `promokit-contract` | 跨模块共享契约：异常类型（CurrencyMismatch/QuotaExhausted/PromotionExpired 等）、Money、Promotion/PromotionType/PromotionStatus 值与枚举。零依赖，被其余所有模块依赖。 |
| `promokit-core` | 零 Spring 核心：Cart/PromotionContext 模型，Condition 门槛，DiscountStrategy 计算策略，PriceEngine 算价与冲突解决（互斥组 / 叠加深度 / 名额），InMemoryQuotaStore 名额控制，多租户上下文。 |
| `promokit-coupon` | 券域：Coupon/CouponTemplate 模型与 CouponService 发券核销，独立容量闸门与并发语义（compute 原子状态切换防重复核销），不依赖算价引擎。 |
| `promokit-starter` | Spring Boot 自动装配：注册算价、名额、券服务等 Bean（`@ConditionalOnMissingBean` 可覆盖），按 `X-Tenant-Id` 隔离租户，提供 `/promokit/quote` 与券接口；Web Bean 仅 servlet 环境下装配。 |
| `promokit-sample` | 示例应用：演示算价明细、券发放核销与名额用尽的表现。 |

## 用法

构造购物车与活动，交给 `PriceEngine` 算价：

```java
Instant start = Instant.now().minusSeconds(60);
Instant end = Instant.now().plus(Duration.ofDays(7));

Cart cart = Cart.of("acme", "u-1001", 2, "APP", List.of(
        new CartItem("sku-1", "book", Money.of("68.00", "CNY"), 1),
        new CartItem("sku-2", "toy", Money.of("52.00", "CNY"), 1)));

Promotion fullReduction = Promotion.builder()
        .promotionId("full-100-20")
        .tenantId("acme")
        .name("满100减20")
        .type(PromotionType.FULL_REDUCTION)
        .strategy(new FullReductionStrategy(Money.of("100.00", "CNY"), Money.of("20.00", "CNY")))
        .window(start, end)
        .exclusionGroup("base")
        .build();

Promotion discount = Promotion.builder()
        .promotionId("discount-90")
        .tenantId("acme")
        .name("全场9折")
        .type(PromotionType.DISCOUNT)
        .strategy(new DiscountRateStrategy(new BigDecimal("0.90")))
        .window(start, end)
        .exclusionGroup("base")
        .build();

PromotionContext ctx = PromotionContext.builder(cart)
        .now(Instant.now())
        .memberLevel(2)
        .build();

PriceResult result = new PriceEngine().quote(List.of(fullReduction, discount), ctx);
// 原价 120.00 CNY；满减与折扣同属互斥组 base，取优惠更大的满减 20.00 CNY
result.applied().forEach(d -> System.out.println(d.promotionId() + " 减 " + d.amount()));
result.rejectedReasons().forEach(System.out::println);
System.out.println(result.payable()); // 100.00 CNY
```

算价结果恒满足 `subtotal - totalDiscount == payable`，优惠超过原价时按原价截断，应付不为负。

带名额的活动先登记额度，再算价：

```java
InMemoryQuotaStore quotaStore = new InMemoryQuotaStore();
Promotion flashSale = Promotion.builder()
        .promotionId("flash-sale")
        .tenantId("acme")
        .name("秒杀直降")
        .type(PromotionType.FLASH_SALE)
        .strategy(new FlashSaleStrategy(Map.of("sku-2", Money.of("30.00", "CNY"))))
        .window(start, end)
        .totalQuota(10)
        .perUserQuota(1)
        .build();
quotaStore.registerQuota(flashSale);

PriceEngine engine = new PriceEngine(quotaStore, 3);
engine.quote(List.of(flashSale), ctx); // 第一次预留名额并生效
engine.quote(List.of(flashSale), ctx); // 每人限 1 次，第二次被剔除
```

发券与核销（`redeem` 重复核销抛 `CouponAlreadyUsedException`，`redeemIfAbsent` 返回已核销的券）：

```java
CouponService couponService = new CouponService();
couponService.registerTemplate(new CouponTemplate(
        "newcomer", "acme", "新人券",
        Money.of("15.00", "CNY"), Money.of("100.00", "CNY"),
        start, end, 100));

Coupon coupon = couponService.issue("newcomer", "u-1001");
Coupon used = couponService.redeem(coupon.code(), ctx);
couponService.isRedeemed(coupon.code()); // true
```

接 Spring Boot 时引入 `promokit-starter`，Bean 会自动装配，直接调接口：

```bash
curl -X POST http://localhost:8080/promokit/quote \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Id: acme' \
  -d '{"userId":"u-1001","currency":"CNY","items":[{"skuId":"sku-1","categoryId":"book","unitPrice":68.00,"quantity":1}]}'
```

配置项（`promokit` 前缀）：`default-currency`（默认 CNY）、`tenant-header`（默认 X-Tenant-Id）、`max-stack-depth`（默认 3）、`default-tenant-id`（请求与线程上下文都没有租户时的兜底租户，默认 default）、`max-templates` / `max-coupons` / `registry-capacity` / `quota-capacity`（各存储的容量上限）。

## 设计决策

以下记录关键设计取舍与其理由（不止于「怎么做」，更强调「为什么」）。

1. **模块拆分：contract / core / coupon / starter / sample。** 跨模块共享的契约（异常、Money、Promotion 值与枚举）抽到 `promokit-contract`，券域独立成 `promokit-coupon`，算价引擎留在 `promokit-core`。券域有自己的容量闸门与并发语义，与算价解耦后可独立演进、独立测试；`core` 仍严禁依赖 Spring。这样 `starter` 只依赖契约与实现，不被算价细节绑架。

2. **名额控制走「预留-确认-回滚」，而非「先扣后补」。** 算价时对带名额的活动先 `reserve`，若后续任一环节（含其他带名额活动）失败，则**整体原子回滚**所有已预留名额，`applied` 列表为空。原因：若先扣名额成功、算价却因别的冲突放弃，名额会被静默吃掉，造成误扣或超发。`InMemoryQuotaStore.evictKey` 只撤限额、保留用量计数，使被淘汰的活动按不限量放行时仍能看到历史用量（`evictedEntryDegradesToUnlimited` 语义）。

3. **叠加深度截断是「静默保留最优 N 个」，不写拒绝原因。** 可叠加活动超过 `maxStackDepth` 时，只保留按 `BEST_FIRST`（优惠额降序 → 优先级升序 → 活动 id 升序）排序的前 N 个，被截掉的不写 `rejectedReasons`。原因：截断是取最优子集的策略性取舍，不是调用方可修正的错误，写拒绝原因会误导调用方以为发生了异常。

4. **互斥组内只保留最优一个，但败者仍参与跨组组合。** 同 `exclusionGroup` 内按 `BEST_FIRST` 取最优，组内败者不再与其他组竞争；不同互斥组之间仍可叠加。原因：互斥是「二选一」语义，跨组叠加是「组合优惠」语义，二者正交。

5. **配置边界守卫：非正数容量 / 叠加深度不抛异常，保留上一个有效值并告警。** `isPositiveCapacity` 与 `setMaxStackDepth(<=0)` 等守卫，遇到非法值时不静默覆盖、也不让应用起不来，而是保留安全默认值并把错误留在日志。原因：这些上限是长生命周期容器的唯一防泄漏闸门，配错直接抛异常会导致应用启动失败，不如降级到上一个有效值。

6. **活动窗口起点 inclusive、终点 exclusive。** `now == start` 即生效，`now == end` 即失效。原因：与「活动在某时刻整点开始 / 结束」的常见运营预期对齐，避免差一错误。

7. **算价结果恒满足 `subtotal - totalDiscount == payable`，优惠超额按原价截断，应付不为负。** 这是防御性收口，避免策略叠加或浮点计算产生负价、错价，把「不可能」的状态在引擎内兜死。

8. **券核销用 `compute` 原子化状态切换，防并发重复折扣。** 核销 / 作废走 `ConcurrentHashMap.compute` 而非 read-then-write，并发重复提交同一券码不会重复扣减（`redeemIfAbsent` 幂等，重复核销返回已核销的券）。

## 构建

```bash
./mvnw -B verify
```

`verify` 会跑编译、单元测试、集成测试和 JaCoCo 门禁（整体 >= 0.60，core >= 0.75）。

## 架构约束

`promokit-core` 严禁依赖 `org.springframework.*`（CI 的 `architecture-boundaries` job 强制扫描）。

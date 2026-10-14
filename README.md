# promokit

促销规则与算价引擎：定义满减 / 折扣 / 阶梯 / 券 / 秒杀活动，按门槛条件筛选可用活动，再按互斥组与叠加规则算出最终价和优惠明细；名额（总名额、每人限用）走预留-确认-回滚，避免超发。core 不依赖 Spring，starter 提供 Spring Boot 自动装配。

## 模块

| 模块 | 说明 |
| --- | --- |
| `promokit-core` | 零 Spring 核心：Money/Cart/Promotion 模型，Condition 门槛，DiscountStrategy 计算策略，PriceEngine 算价与冲突解决，QuotaStore 名额控制，CouponService 发券核销，多租户上下文。 |
| `promokit-starter` | Spring Boot 自动装配：注册算价、名额、券服务等 Bean，按 `X-Tenant-Id` 隔离租户，提供 `/promokit/quote` 与券接口。 |
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

配置项（`promokit` 前缀）：`default-currency`（默认 CNY）、`tenant-header`（默认 X-Tenant-Id）、`max-stack-depth`（默认 3）、`default-tenant-id`（请求与线程上下文都没有租户时的兜底租户，默认 default）。

## 构建

```bash
./mvnw -B verify
```

`verify` 会跑编译、单元测试、集成测试和 JaCoCo 门禁（整体 >= 0.60，core >= 0.75）。

## 架构约束

`promokit-core` 严禁依赖 `org.springframework.*`（CI 的 `architecture-boundaries` job 强制扫描）。

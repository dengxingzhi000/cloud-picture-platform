# SCM 关键服务代码提交流程 (Critical Services)

适用于 SCM 平台的关键业务服务:
- **scm-order** (订单处理服务 - 核心交易链路)
- **scm-finance** (财务结算服务 - 资金安全)

这些服务直接涉及**交易、资金、结算**等核心业务,对**数据一致性、安全性、可靠性**要求极高。

## 准备工作

1. **暂存区确认:** 已将所有相关的代码变更添加到 `git` 暂存区
2. **禁止变更:** 此阶段严禁对暂存区进行任何修改
3. **梳理总结:** 使用 `git status --short` 和 `git diff --cached` 阅读暂存区内容

## 关键服务特定检查

关键服务是平台的**生命线**,任何故障都可能导致严重的业务和财务损失。

### 1. 构建验证 (强制通过)

```bash
# 进入对应服务目录
cd scm-order  # 或 scm-finance

# 构建验证 (禁止跳过测试)
mvn clean verify -DskipTests=false
```

**强制要求:**
- 所有单元测试必须通过 (100%)
- 所有集成测试必须通过 (100%)
- 代码覆盖率 >= 90%

### 2. 分布式事务检查 (强制)

关键服务几乎所有操作都涉及分布式事务:

```bash
# 检查 @GlobalTransactional 使用
grep -rn "@GlobalTransactional" service/src/main/java/

# 检查 undo_log 表
psql -U admin -d db_order -c "\d undo_log"
psql -U admin -d db_finance -c "\d undo_log"
```

**关键检查点:**
- [ ] 所有跨服务操作标记了 `@GlobalTransactional`
- [ ] 事务超时配置合理 (默认 60s,根据业务调整)
- [ ] 回滚策略正确 (rollbackFor = Exception.class)
- [ ] 数据库有 undo_log 表

**订单创建事务示例:**
```java
@Service
public class OrderService {

    @Autowired
    private InventoryServiceClient inventoryClient;

    @Autowired
    private PaymentServiceClient paymentClient;

    @GlobalTransactional(
        name = "create-order-and-payment",
        rollbackFor = Exception.class,
        timeoutMills = 120000  // 2分钟超时
    )
    public Order createOrder(OrderDTO dto) {
        // 1. 创建订单 (本地事务)
        Order order = new Order(dto);
        orderMapper.insert(order);

        // 2. 扣减库存 (远程RPC,参与全局事务)
        inventoryClient.deductStock(dto.getSkuId(), dto.getQuantity(), order.getOrderNo());

        // 3. 创建支付单 (远程RPC,参与全局事务)
        paymentClient.createPayment(order.getId(), dto.getAmount());

        // 任何步骤失败,Seata 自动回滚所有操作
        return order;
    }
}
```

### 3. 幂等性检查 (强制)

**关键操作必须保证幂等性,防止重复执行导致资金/库存错误:**

```bash
# 检查幂等性实现
grep -rn "requestId\|idempotent\|redis.*set.*NX" service/src/main/java/
```

**必须实现幂等性的操作:**
- [ ] 订单创建 (order)
- [ ] 订单支付 (order)
- [ ] 订单取消 (order)
- [ ] 退款处理 (finance)
- [ ] 结算处理 (finance)
- [ ] 库存扣减 (已在 inventory 实现)

**幂等性实现模式:**
```java
@Service
public class OrderPaymentService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    /**
     * 订单支付 (幂等性保证)
     */
    @GlobalTransactional(name = "pay-order", rollbackFor = Exception.class)
    public PaymentResult payOrder(Long orderId, String paymentRequestId) {
        // 1. 幂等性检查 (使用 Redis SETNX)
        String idempotentKey = "pay:" + paymentRequestId;
        Boolean isFirstRequest = redisTemplate.opsForValue()
            .setIfAbsent(idempotentKey, "processing", 10, TimeUnit.MINUTES);

        if (Boolean.FALSE.equals(isFirstRequest)) {
            // 已处理,返回之前的结果
            return getPaymentResult(paymentRequestId);
        }

        try {
            // 2. 执行支付逻辑
            Order order = orderMapper.selectById(orderId);
            if (order == null) {
                throw new BusinessException("订单不存在");
            }

            if (order.getStatus() == OrderStatus.PAID) {
                return PaymentResult.success("订单已支付");
            }

            // 调用支付服务
            PaymentResult result = paymentClient.pay(order.getOrderNo(), order.getAmount());

            // 更新订单状态
            order.setStatus(OrderStatus.PAID);
            order.setPaidAt(LocalDateTime.now());
            orderMapper.updateById(order);

            // 3. 保存处理结果 (24小时过期)
            savePaymentResult(paymentRequestId, result);
            redisTemplate.opsForValue().set(idempotentKey, "completed", 24, TimeUnit.HOURS);

            return result;

        } catch (Exception e) {
            // 处理失败,删除幂等性标记,允许重试
            redisTemplate.delete(idempotentKey);
            throw e;
        }
    }
}
```

### 4. 状态机检查 (scm-order)

**订单状态流转必须严格遵循状态机:**

```bash
# 检查状态机配置
find . -name "*StateMachine*" -o -name "*OrderState*"
```

**订单状态流转:**
```
PENDING_PAYMENT → PAID → PENDING_SHIP → SHIPPED → IN_TRANSIT → DELIVERED → COMPLETED
      ↓
  CANCELLED (仅未支付订单可取消)
```

**状态机配置示例:**
```java
@Configuration
@EnableStateMachine
public class OrderStateMachineConfig extends StateMachineConfigurerAdapter<OrderState, OrderEvent> {

    @Override
    public void configure(StateMachineStateConfigurer<OrderState, OrderEvent> states) throws Exception {
        states
            .withStates()
            .initial(OrderState.PENDING_PAYMENT)
            .states(EnumSet.allOf(OrderState.class))
            .end(OrderState.COMPLETED)
            .end(OrderState.CANCELLED);
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<OrderState, OrderEvent> transitions) throws Exception {
        transitions
            // 支付
            .withExternal()
                .source(OrderState.PENDING_PAYMENT).target(OrderState.PAID)
                .event(OrderEvent.PAY)
                .action(confirmInventoryReservationAction())
            // 发货
            .and().withExternal()
                .source(OrderState.PAID).target(OrderState.PENDING_SHIP)
                .event(OrderEvent.CONFIRM)
            // 取消 (仅未支付订单)
            .and().withExternal()
                .source(OrderState.PENDING_PAYMENT).target(OrderState.CANCELLED)
                .event(OrderEvent.CANCEL)
                .action(releaseInventoryReservationAction());
    }
}
```

### 5. 数据一致性检查

**关键服务必须保证强一致性或最终一致性:**

**强一致性 (Seata AT 模式):**
- 订单创建 + 库存扣减
- 订单支付 + 支付单创建
- 退款处理 + 资金退回

**最终一致性 (消息补偿):**
- 订单状态同步到数据仓库
- 积分赠送 (失败后通过定时任务补偿)
- 优惠券退回 (失败后通过定时任务补偿)

**补偿机制示例:**
```java
@Service
public class OrderCompensationService {

    /**
     * 定时补偿失败的积分赠送
     */
    @XxlJob("orderPointsCompensationJob")
    public void compensatePoints() {
        // 1. 查询已支付但积分未赠送的订单
        List<Order> orders = orderMapper.selectList(
            Wrappers.<Order>lambdaQuery()
                .eq(Order::getStatus, OrderStatus.PAID)
                .eq(Order::getPointsGranted, false)
                .lt(Order::getPaidAt, LocalDateTime.now().minusMinutes(5))  // 5分钟前
        );

        // 2. 补偿积分赠送
        for (Order order : orders) {
            try {
                pointsClient.grantPoints(order.getUserId(), order.getPoints());
                order.setPointsGranted(true);
                orderMapper.updateById(order);
            } catch (Exception e) {
                log.error("积分补偿失败: orderId={}", order.getId(), e);
                // 记录补偿失败,人工介入
            }
        }
    }
}
```

### 6. 资金安全检查 (scm-finance)

**财务服务涉及资金流转,安全性要求极高:**

**强制检查点:**
- [ ] 金额计算使用 `BigDecimal` (禁止使用 float/double)
- [ ] 金额字段精度正确 (DECIMAL(19,2))
- [ ] 所有金额操作记录审计日志
- [ ] 敏感操作需要双重验证
- [ ] 退款金额不超过原支付金额

**金额计算示例:**
```java
@Service
public class FinanceCalculationService {

    /**
     * 计算订单应付金额
     * ✅ 使用 BigDecimal 保证精度
     */
    public BigDecimal calculatePayableAmount(Order order) {
        // 商品总价
        BigDecimal totalPrice = order.getTotalPrice();

        // 运费
        BigDecimal freight = order.getFreight();

        // 优惠金额
        BigDecimal discount = order.getDiscountAmount();

        // 应付金额 = 商品总价 + 运费 - 优惠
        BigDecimal payableAmount = totalPrice
            .add(freight)
            .subtract(discount);

        // 金额不能为负
        if (payableAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("应付金额不能为负");
        }

        // 保留2位小数,四舍五入
        return payableAmount.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 退款金额验证
     */
    public void validateRefundAmount(BigDecimal refundAmount, BigDecimal paidAmount) {
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("退款金额必须大于0");
        }

        if (refundAmount.compareTo(paidAmount) > 0) {
            throw new BusinessException("退款金额不能超过实付金额");
        }
    }
}
```

### 7. 数据库分区表检查

**订单表和财务表使用 PostgreSQL 分区表:**

**检查点:**
- [ ] UNIQUE 约束包含分区键
- [ ] 订单表: `UNIQUE (order_no, create_time)`
- [ ] 支付记录表: 无 UNIQUE (日志表)
- [ ] 运费计算表: 无 UNIQUE (日志表)

```sql
-- ✅ 正确: 订单表 UNIQUE 约束包含分区键
CREATE TABLE ord_order (
    id UUID DEFAULT gen_random_uuid(),
    order_no VARCHAR(128) NOT NULL,
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_order_no_create_time UNIQUE (order_no, create_time)
) PARTITION BY RANGE (create_time);

-- ✅ 正确: 日志表无 UNIQUE 约束
CREATE TABLE payment_record (
    id UUID DEFAULT gen_random_uuid(),
    order_no VARCHAR(128) NOT NULL,
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW()
) PARTITION BY RANGE (create_time);
```

### 8. 单元测试 (覆盖率 >= 90%)

**关键服务要求极高的测试覆盖率:**

```bash
mvn test jacoco:report

# 检查覆盖率
# target/site/jacoco/index.html
```

**关键测试场景:**
- [ ] 正常流程 (下单、支付、发货、收货)
- [ ] 异常场景 (库存不足、支付失败、超时)
- [ ] 边界条件 (金额为0、负数、超大金额)
- [ ] 并发场景 (重复支付、重复退款)
- [ ] 分布式事务回滚
- [ ] 幂等性验证
- [ ] 状态机流转

**并发测试示例:**
```java
@Test
public void testConcurrentPayment() throws Exception {
    Long orderId = 1L;
    String paymentRequestId = "PAY20250129001";

    int threadCount = 10;
    CountDownLatch latch = new CountDownLatch(threadCount);
    AtomicInteger successCount = new AtomicInteger(0);

    // 模拟10个线程同时支付同一订单
    for (int i = 0; i < threadCount; i++) {
        new Thread(() -> {
            try {
                PaymentResult result = orderPaymentService.payOrder(orderId, paymentRequestId);
                if (result.isSuccess()) {
                    successCount.incrementAndGet();
                }
            } finally {
                latch.countDown();
            }
        }).start();
    }

    latch.await();

    // 验证只有一次支付成功
    assertEquals(1, successCount.get());

    // 验证订单状态正确
    Order order = orderMapper.selectById(orderId);
    assertEquals(OrderStatus.PAID, order.getStatus());
}
```

### 9. 集成测试 (端到端)

**关键服务必须进行完整的端到端测试:**

```bash
# 启动完整环境
docker-compose up -d

# 运行集成测试
mvn verify -P integration-test

# 关键场景测试
# - 订单创建 → 支付 → 发货 → 收货
# - 订单创建 → 取消 → 库存释放
# - 订单支付 → 退款 → 资金退回
```

### 10. 性能测试 (压测)

**关键服务必须通过性能测试:**

**性能指标要求:**
- 订单创建 TPS >= 500
- 订单查询 TPS >= 2000
- 支付处理 TPS >= 300
- P95 响应时间 < 300ms
- P99 响应时间 < 500ms

**压测工具:**
```bash
# 使用 JMeter 压测
jmeter -n -t order-creation.jmx -l result.jtl -e -o report/

# 使用 wrk 压测
wrk -t10 -c100 -d30s --latency http://localhost:8203/api/v1/orders
```

## 标准流程

### 1. 创建 Issue

```bash
gh issue create \
  --title "feat(order): 添加订单自动确认收货功能" \
  --body "$(cat <<EOF
## 背景
当前订单发货后需要用户手动确认收货,很多用户忘记确认导致结算延迟。

## 需求描述
- 订单发货后 7 天自动确认收货
- 确认收货后自动触发结算
- 支持用户手动提前确认
- 记录确认方式 (手动/自动)

## 技术方案
- 使用 XXL-Job 定时扫描待确认订单
- 发货时间超过 7 天自动确认
- 确认后触发状态机流转
- 通过 Kafka 发送结算消息

## 验收标准
- [ ] 自动确认准确率 100%
- [ ] 确认后立即触发结算
- [ ] 单元测试覆盖率 >= 90%
- [ ] 集成测试覆盖完整流程
- [ ] 压测 TPS >= 500

## 数据一致性保证
- 使用分布式事务确保确认+结算原子性
- 幂等性保证 (防止重复确认)
- 补偿机制 (结算失败后重试)

## 性能影响评估
- 定时任务每小时执行一次
- 单次扫描预计处理 1000 订单
- 预计耗时 < 30 秒

## 资金安全评估
- 确认收货后资金立即结算给商家
- 需要防止恶意自动确认
- 需要记录审计日志

## 相关文档
- 需求文档: docs/requirements/AUTO_CONFIRM_ORDER.md
- 状态机设计: docs/design/ORDER_STATE_MACHINE.md
EOF
)" \
  --label "type/feature,priority/P1,domain/order,critical,security-review"
```

**Issue 内容要求 (关键服务额外要求):**
- 必须包含**数据一致性保证**方案
- 必须包含**资金安全评估** (finance)
- 必须包含**性能指标**要求
- 必须添加 `critical` 标签
- 必须添加 `security-review` 标签

### 2. 创建特性分支

```bash
git checkout master
git pull origin master

git checkout -b feat/order-auto-confirm-#123
```

### 3. 提交变更

```bash
git commit
```

**Commit Message 示例:**

```
feat(order): 添加订单自动确认收货功能

- 实现OrderAutoConfirmService自动确认服务
- 添加XXL-Job定时扫描任务
- 集成订单状态机,自动流转到DELIVERED状态
- 通过Kafka发送结算消息到财务服务
- 实现幂等性保证 (防止重复确认)
- 添加完整的单元测试和集成测试

技术细节:
- 使用Redis分布式锁防止并发确认
- 幂等性KEY: auto-confirm:{orderId}
- 状态机事件: OrderEvent.AUTO_CONFIRM
- Kafka Topic: order.confirmed

数据一致性:
- @GlobalTransactional 保证确认+结算原子性
- 补偿机制: 结算失败后每小时重试

性能指标:
- 单次扫描 1000 订单,耗时 25 秒
- 确认处理延迟 < 1 分钟
- 内存占用增加约 20MB

安全措施:
- 记录确认方式 (手动/自动)
- 记录审计日志
- 防止恶意自动确认 (检查发货时间)

测试覆盖率: 92%
压测 TPS: 580

Closes #123
```

### 4. 推送代码并创建 PR

```bash
git push -u origin feat/order-auto-confirm-#123

gh pr create \
  --title "feat(order): 添加订单自动确认收货功能" \
  --body "$(cat <<EOF
## 变更说明
实现订单发货 7 天后自动确认收货,并触发结算流程。

## 主要变更
- 新增自动确认服务和定时任务
- 集成订单状态机
- 新增结算消息发送

## 测试说明
- 单元测试覆盖率: 92%
- 集成测试: 已验证完整流程 (确认 → 结算)
- 压测结果: TPS=580, P95=280ms, P99=450ms
- 分布式事务测试: 回滚场景已验证

## 数据一致性保证
- ✅ 使用 Seata 分布式事务
- ✅ 幂等性保证 (Redis)
- ✅ 补偿机制 (结算失败重试)

## 资金安全评估
- ✅ 防止恶意自动确认 (检查发货时间)
- ✅ 记录审计日志 (确认方式、确认时间)
- ✅ 结算前二次验证订单状态

## 性能影响
- 定时任务每小时执行,CPU 占用 < 5%
- 内存占用增加约 20MB
- Kafka 消息量增加 (每天约 10000 条)

## 数据库变更
- 新增字段: ord_order.confirm_type (手动/自动)
- 索引: idx_shipped_at (用于扫描)

## 配置变更
- order.auto-confirm.days=7 (自动确认天数)

## 回滚方案
- 关闭定时任务即可
- 无数据库结构变更,可快速回滚

Closes #123
EOF
)" \
  --label "type/feature,priority/P1,domain/order,critical,security-review"
```

### 5. 代码审查

**审查者特殊关注点 (关键服务):**
- [ ] 分布式事务使用正确
- [ ] 幂等性实现完善
- [ ] 资金计算精度正确 (BigDecimal)
- [ ] 状态机流转合理
- [ ] 数据一致性保证
- [ ] 异常处理完善
- [ ] 审计日志完整
- [ ] 补偿机制可靠
- [ ] 性能测试通过
- [ ] 压测指标达标

**强制要求:**
- 至少 **3 名审查者** (架构师、业务负责人、安全)
- **架构师必须批准**
- **业务负责人必须批准**
- **安全团队必须批准** (涉及资金)

### 6. 合并代码

**合并前检查清单 (关键服务额外要求):**
- [ ] 所有 CI/CD 检查通过
- [ ] 至少 3 名审查者批准
- [ ] 单元测试覆盖率 >= 90%
- [ ] 集成测试全量通过
- [ ] 性能测试通过 (TPS、延迟达标)
- [ ] 压测报告已提交
- [ ] 预发布环境验证 >= 2 天
- [ ] 数据一致性测试通过
- [ ] 分布式事务回滚测试通过
- [ ] 灰度发布计划已准备
- [ ] 监控告警已配置
- [ ] 回滚方案已准备

**灰度发布计划:**
1. 预发布环境验证 2-3 天
2. 生产环境灰度发布:
   - 第1天: 5% 流量
   - 第2天: 20% 流量
   - 第3天: 50% 流量
   - 第5天: 100% 流量
3. 每个阶段观察关键指标:
   - 订单创建成功率
   - 支付成功率
   - 接口响应时间
   - 错误率
   - 数据库连接数
   - Redis 命中率

## GitHub Labels 规范

**必需标签:**
- **优先级:** `priority/P0` ~ `priority/P3`
- **类型:** `type/feature`, `type/bug`, `type/security` 等
- **领域:** `domain/order`, `domain/finance`

**特殊标签 (关键服务强制):**
- `critical` - 关键服务 (**强制标注**)
- `security-review` - 需要安全审查 (**强制标注**)
- `distributed-transaction` - 涉及分布式事务
- `financial` - 涉及资金流转 (finance 服务)
- `requires-pressure-test` - 需要压测
- `requires-gradual-release` - 需要灰度发布

## 最佳实践

### 1. 订单并发控制

**使用分布式锁防止订单重复创建:**

```java
@Service
public class OrderCreationService {

    @Autowired
    private DistributedLock distributedLock;

    /**
     * 创建订单 (防止重复提交)
     */
    public Order createOrder(OrderDTO dto, String requestId) {
        // 1. 分布式锁 (防止并发创建)
        String lockKey = "create-order:" + dto.getUserId();
        LockHandle lock = distributedLock.acquire(lockKey, 30, TimeUnit.SECONDS);

        try {
            // 2. 幂等性检查
            if (redisTemplate.hasKey("order:" + requestId)) {
                return getExistingOrder(requestId);
            }

            // 3. 创建订单
            Order order = doCreateOrder(dto);

            // 4. 标记已创建
            redisTemplate.opsForValue().set("order:" + requestId, order.getId().toString(), 24, TimeUnit.HOURS);

            return order;

        } finally {
            lock.release();
        }
    }
}
```

### 2. 资金计算精度

**财务计算必须使用 BigDecimal:**

```java
@Service
public class FinanceService {

    /**
     * ✅ 正确: 使用 BigDecimal
     */
    public BigDecimal calculateRefundAmount(Order order) {
        BigDecimal paidAmount = order.getPaidAmount();
        BigDecimal freight = order.getFreight();

        // 退款金额 = 实付金额 - 运费
        BigDecimal refundAmount = paidAmount.subtract(freight);

        // 保留2位小数,四舍五入
        return refundAmount.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * ❌ 错误: 使用 double (精度丢失)
     */
    public double calculateRefundAmountWrong(Order order) {
        double paidAmount = order.getPaidAmount().doubleValue();
        double freight = order.getFreight().doubleValue();
        return paidAmount - freight;  // ❌ 可能导致精度丢失
    }
}
```

### 3. 订单状态机

**完整的订单状态机实现:**

参考 scm-order/service/src/main/java/com/frog/order/statemachine/OrderStateMachineConfig.java

### 4. 补偿任务

**定时补偿失败的操作:**

```java
@Component
public class OrderCompensationJob {

    /**
     * 补偿失败的积分赠送
     */
    @XxlJob("orderPointsCompensationJob")
    @Scheduled(cron = "0 */30 * * * ?")  // 每30分钟
    public void compensatePoints() {
        List<Order> orders = orderMapper.selectList(
            Wrappers.<Order>lambdaQuery()
                .eq(Order::getStatus, OrderStatus.PAID)
                .eq(Order::getPointsGranted, false)
                .lt(Order::getPaidAt, LocalDateTime.now().minusMinutes(30))
        );

        for (Order order : orders) {
            try {
                pointsClient.grantPoints(order.getUserId(), order.getPoints());
                order.setPointsGranted(true);
                orderMapper.updateById(order);
                log.info("积分补偿成功: orderId={}", order.getId());
            } catch (Exception e) {
                log.error("积分补偿失败: orderId={}", order.getId(), e);
                // 记录到人工处理队列
                manualHandlingService.record("POINTS_GRANT_FAILED", order.getId());
            }
        }
    }
}
```

## 常见问题

**Q: 分布式事务失败后如何排查?**

A: 检查 Seata 日志和 undo_log:
```bash
# 1. 查看 Seata Server 日志
docker logs seata-server

# 2. 查看服务的 Seata Client 日志
grep "io.seata" scm-order/logs/app.log

# 3. 查看 undo_log 表
psql -U admin -d db_order -c "SELECT * FROM undo_log ORDER BY log_created DESC LIMIT 10;"

# 4. 检查全局事务状态
# Seata Console: http://localhost:7091
```

**Q: 如何防止订单重复支付?**

A: 多层防护:
1. 前端防抖 (禁用按钮)
2. 幂等性检查 (requestId)
3. 订单状态检查 (已支付不能再支付)
4. 分布式锁 (同一订单同时只能一个支付请求)

**Q: 财务数据不一致如何处理?**

A: 对账机制:
```java
@XxlJob("financeReconciliationJob")
@Scheduled(cron = "0 0 2 * * ?")  // 每天凌晨2点
public void reconcile() {
    // 1. 对账订单金额 vs 支付记录
    // 2. 对账退款金额 vs 退款记录
    // 3. 对账结算金额 vs 商家账户
    // 4. 发现差异记录到差异表,人工处理
}
```

**Q: 如何进行压力测试?**

A: 使用 JMeter:
```bash
# 1. 准备测试数据 (创建测试用户、商品)
# 2. 编写 JMeter 测试脚本
# 3. 执行压测
jmeter -n -t order-creation.jmx -l result.jtl -e -o report/

# 4. 分析报告
# - TPS (每秒事务数)
# - 响应时间 (P50, P95, P99)
# - 错误率
# - 数据库连接数
# - CPU/内存使用率
```

---

**文档版本:** v1.0
**适用服务:** scm-order (订单服务), scm-finance (财务服务)
**最后更新:** 2025-12-29

**警告:** 关键服务的任何变更都可能影响业务和资金安全,必须严格遵循此流程,充分测试后再发布!
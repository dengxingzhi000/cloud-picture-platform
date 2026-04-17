# SCM 业务服务代码提交流程 (Business Services)

适用于 SCM 平台的业务服务模块:
- **scm-product** (产品目录服务)
- **scm-inventory** (库存管理服务)
- **scm-warehouse** (仓库管理 WMS)
- **scm-logistics** (物流追踪 TMS)
- **scm-supplier** (供应商管理)
- **scm-purchase** (采购管理)

**注意:** scm-order (订单服务) 和 scm-finance (财务服务) 属于关键服务,使用 `critical-service.md` workflow。

## 准备工作

1. **暂存区确认:** 已将所有相关的代码变更添加到 `git` 暂存区
2. **禁止变更:** 此阶段严禁对暂存区进行任何修改
3. **梳理总结:** 使用 `git status --short` 和 `git diff --cached` 阅读暂存区内容

## 业务服务特定检查

业务服务是供应链管理的核心功能,需要关注**数据一致性、高并发性能和业务逻辑正确性**。

### 1. 构建验证

```bash
# 进入对应服务目录
cd scm-product  # 或 scm-inventory, scm-warehouse 等

# 构建验证 (必须通过)
mvn clean verify
```

### 2. 分布式事务检查

业务服务经常涉及跨服务操作,**必须正确使用 Seata 分布式事务**:

```bash
# 检查 @GlobalTransactional 注解使用
grep -r "@GlobalTransactional" service/src/main/java/
```

**关键检查点:**
- [ ] 跨服务操作标记了 `@GlobalTransactional`
- [ ] 事务名称清晰 (name 参数)
- [ ] 正确配置了 rollbackFor = Exception.class
- [ ] 数据库有 undo_log 表

**示例:**
```java
@GlobalTransactional(name = "create-purchase-order", rollbackFor = Exception.class)
public PurchaseOrder createOrder(PurchaseOrderDTO dto) {
    // 1. 创建采购单
    PurchaseOrder order = orderMapper.insert(dto);

    // 2. 通知供应商 (跨服务调用,自动参与全局事务)
    supplierClient.notifyNewOrder(order.getSupplierId(), order.getId());

    // 3. 预占库存 (跨服务调用,自动参与全局事务)
    inventoryClient.reserveStock(order.getItems());

    return order;
}
```

### 3. 幂等性检查

业务操作必须保证幂等性,防止重复执行:

```bash
# 检查关键操作的幂等性实现
grep -r "requestId\|idempotent" service/src/main/java/
```

**关键操作:**
- [ ] 库存扣减 (inventory)
- [ ] 入库出库 (warehouse)
- [ ] 物流状态更新 (logistics)
- [ ] 采购单创建 (purchase)

**幂等性模式示例:**
```java
public boolean deductStock(Long skuId, Integer quantity, String requestId) {
    // 1. 检查是否已处理
    String key = "deduct:" + requestId;
    if (redisTemplate.hasKey(key)) {
        return true; // 已处理,直接返回成功
    }

    // 2. 执行扣减 (使用 Lua 脚本保证原子性)
    boolean success = stockRedisService.deductStock(skuId, quantity);

    // 3. 标记已处理 (24小时过期)
    if (success) {
        redisTemplate.opsForValue().set(key, "1", 24, TimeUnit.HOURS);
    }

    return success;
}
```

### 4. 高并发性能检查

业务服务需要应对高并发场景 (如秒杀、大促):

**性能要求:**
- 库存查询响应时间 < 50ms
- 库存扣减响应时间 < 100ms
- 订单创建响应时间 < 300ms
- 列表查询响应时间 < 200ms

**性能优化检查:**
- [ ] 热点数据使用 Redis 缓存
- [ ] 库存操作使用 Lua 脚本 (原子性)
- [ ] 数据库查询使用分页
- [ ] 复杂查询使用 Elasticsearch
- [ ] 使用 @Async 异步处理非关键流程

### 5. 数据库设计检查

**分区表检查 (如适用):**

某些业务表使用 PostgreSQL 分区:
- `inv_reservation` (库存预留) - 按 reserved_at 分区
- `freight_calc_record` (运费计算) - 按 create_time 分区

**检查点:**
- [ ] UNIQUE 约束包含分区键 (PostgreSQL 要求)
- [ ] 示例: `UNIQUE (reservation_no, reserved_at)`

**索引检查:**
- [ ] 查询条件字段有索引
- [ ] 复合索引顺序正确 (高选择性字段在前)
- [ ] 外键字段有索引

### 6. 单元测试 (覆盖率 >= 80%)

```bash
mvn test jacoco:report

# 检查覆盖率
# target/site/jacoco/index.html
```

**关键测试场景:**
- [ ] 正常业务流程
- [ ] 边界条件 (库存不足、超出限额等)
- [ ] 并发场景 (使用 CountDownLatch 模拟)
- [ ] 异常处理 (超时、服务不可用等)

### 7. 集成测试

```bash
# 启动依赖基础设施
docker-compose up -d nacos postgres redis kafka seata

# 运行集成测试
mvn verify -P integration-test
```

**集成测试覆盖:**
- [ ] 完整业务流程 (端到端)
- [ ] 分布式事务回滚
- [ ] 消息发送和消费
- [ ] 缓存更新和失效

### 8. Elasticsearch 同步检查 (如适用)

**适用服务:** scm-product (产品搜索)

**检查点:**
- [ ] 实体类添加了 `@Document` 注解
- [ ] 字段使用了正确的 `@Field` 类型
- [ ] 中文字段使用 `ik_max_word` 分词器
- [ ] Canal 配置了正确的表监听

**示例:**
```java
@Document(indexName = "products")
public class ProductDocument {
    @Id
    private Long id;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String productName;

    @Field(type = FieldType.Keyword)
    private String productCode;

    @Field(type = FieldType.Double)
    private BigDecimal price;
}
```

## 标准流程

### 1. 创建 Issue

```bash
gh issue create \
  --title "feat(inventory): 添加库存预警功能" \
  --body "$(cat <<EOF
## 背景
当前系统无法及时发现库存不足,导致缺货影响业务。

## 需求描述
- 支持配置库存预警阈值 (SKU级别)
- 库存低于阈值时自动发送预警通知
- 支持预警历史查询和统计

## 技术方案
- 使用 XXL-Job 定时扫描库存
- 预警阈值存储在 Redis,支持动态调整
- 通过 Kafka 发送预警消息给通知服务
- 预警记录存储在 PostgreSQL

## 验收标准
- [ ] 预警检测延迟 < 5分钟
- [ ] 支持批量设置预警阈值
- [ ] 预警通知成功率 >= 99%
- [ ] 单元测试覆盖率 >= 80%

## 性能影响评估
- 定时任务每5分钟执行一次
- 单次扫描预计耗时 < 30秒 (10万SKU)
- Redis 内存占用增加约 50MB

## 依赖服务
- scm-notify (通知服务)
- XXL-Job (定时任务调度)

## 相关文档
- 需求文档: docs/requirements/INVENTORY_ALERT.md
EOF
)" \
  --label "type/feature,priority/P2,domain/inventory"
```

**Issue 内容要求:**
- 必须包含**性能影响评估**
- 必须说明**依赖服务**
- 必须说明**数据量规模**

### 2. 创建特性分支

```bash
# 更新主分支
git checkout master
git pull origin master

# 创建特性分支
git checkout -b feat/inventory-alert-#123
```

**分支命名格式:** `<type>/<service>-<short-desc>-#<issue>`

**服务名缩写:**
- `product` - scm-product
- `inventory` - scm-inventory
- `warehouse` - scm-warehouse
- `logistics` - scm-logistics
- `supplier` - scm-supplier
- `purchase` - scm-purchase

### 3. 提交变更

```bash
git commit
```

**Commit Message 示例:**

```
feat(inventory): 添加库存预警功能

- 实现InventoryAlertService库存预警服务
- 添加InventoryAlertConfig预警配置管理
- 实现XXL-Job定时扫描任务
- 通过Kafka发送预警消息到通知服务
- 添加预警历史查询接口
- 完善单元测试和集成测试

技术细节:
- 预警阈值存储在Redis,支持动态调整
- 使用Caffeine缓存SKU基础信息,减少数据库查询
- 批量查询库存,单次处理1000条记录
- 异步发送Kafka消息,不阻塞主流程

性能指标:
- 单次扫描耗时: 25秒 (10万SKU)
- 预警检测延迟: < 5分钟
- 内存占用增加: 约50MB

测试覆盖率: 82%

Closes #123
```

**关键要求:**
- 必须包含**技术细节**说明
- 必须包含**性能指标**
- 必须说明**异步处理**和**批量处理**策略

### 4. 推送代码并创建 PR

```bash
# 推送分支
git push -u origin feat/inventory-alert-#123

# 创建 Pull Request
gh pr create \
  --title "feat(inventory): 添加库存预警功能" \
  --body "$(cat <<EOF
## 变更说明
实现库存预警功能,当库存低于阈值时自动发送通知。

## 主要变更
- 新增库存预警服务和定时任务
- 新增预警配置管理接口
- 新增预警历史查询接口

## 测试说明
- 单元测试覆盖率: 82%
- 集成测试: 已验证完整预警流程
- 性能测试: 10万SKU扫描耗时 25秒

## 性能影响
- 定时任务每5分钟执行,CPU占用 < 10%
- Redis内存占用增加约 50MB
- Kafka消息量增加 (预警触发时)

## 数据库变更
- 新增表: inv_alert_config, inv_alert_history
- 索引: idx_sku_id, idx_create_time

## 配置变更
- 新增配置: inventory.alert.scan-interval=300000 (见 application.yml)

## 依赖服务
- scm-notify (通知服务) - 接收预警消息
- XXL-Job - 定时任务调度

## 向后兼容性
✅ 完全向后兼容,新增功能不影响现有业务

Closes #123
EOF
)" \
  --label "type/feature,priority/P2,domain/inventory"
```

### 5. 代码审查

**审查者关注点 (业务服务):**
- [ ] 业务逻辑正确性
- [ ] 分布式事务使用正确
- [ ] 幂等性保证
- [ ] 高并发性能优化
- [ ] 缓存策略合理
- [ ] 数据库索引优化
- [ ] 消息发送可靠性
- [ ] 异常处理完善

**性能审查:**
- [ ] 是否有 N+1 查询问题
- [ ] 大数据量操作是否分批处理
- [ ] 是否合理使用缓存
- [ ] 是否使用数据库连接池

**数据一致性审查:**
- [ ] 分布式事务边界清晰
- [ ] 最终一致性保证 (消息补偿机制)
- [ ] 幂等性实现正确

### 6. 合并代码

**合并前检查清单:**
- [ ] 所有 CI/CD 检查通过
- [ ] 至少 1 名审查者批准
- [ ] 代码冲突已解决
- [ ] 性能测试通过 (如涉及高并发场景)
- [ ] 数据库迁移脚本已准备

**合并策略:**
- 使用 `Squash and Merge` 保持历史清晰

## GitHub Labels 规范

**必需标签:**
- **优先级:** `priority/P0` ~ `priority/P3`
- **类型:** `type/feature`, `type/bug`, `type/perf` 等
- **领域:** `domain/product`, `domain/inventory`, `domain/warehouse` 等

**特殊标签:**
- `performance` - 涉及性能优化
- `distributed-transaction` - 涉及分布式事务
- `high-concurrency` - 涉及高并发场景
- `database-migration` - 涉及数据库变更
- `elasticsearch` - 涉及搜索功能

## 最佳实践

### 1. 库存操作最佳实践 (scm-inventory)

**使用 Redis Lua 脚本保证原子性:**

```java
@Service
public class StockRedisService {

    /**
     * 扣减库存 (原子操作)
     */
    public boolean deductStock(Long skuId, Integer quantity) {
        String script = """
            local stock = redis.call('GET', KEYS[1])
            if tonumber(stock) >= tonumber(ARGV[1]) then
                redis.call('DECRBY', KEYS[1], ARGV[1])
                return 1
            else
                return 0
            end
            """;

        Long result = redisTemplate.execute(
            new DefaultRedisScript<>(script, Long.class),
            Collections.singletonList("stock:" + skuId),
            quantity
        );

        return result != null && result == 1;
    }
}
```

### 2. 分布式事务最佳实践

**场景:** 创建采购单 → 通知供应商 → 预占库存

```java
@Service
public class PurchaseOrderService {

    @Autowired
    private SupplierServiceClient supplierClient;

    @Autowired
    private InventoryServiceClient inventoryClient;

    @GlobalTransactional(name = "create-purchase-order", rollbackFor = Exception.class)
    public PurchaseOrder createOrder(PurchaseOrderDTO dto) {
        // 1. 创建采购单 (本地事务)
        PurchaseOrder order = new PurchaseOrder(dto);
        orderMapper.insert(order);

        // 2. 通知供应商 (远程RPC,参与全局事务)
        supplierClient.notifyNewOrder(order.getSupplierId(), order.getId());

        // 3. 预占库存 (远程RPC,参与全局事务)
        inventoryClient.reserveStock(order.getItems());

        // 任何步骤失败,Seata 自动回滚所有操作
        return order;
    }
}
```

### 3. 消息可靠性最佳实践

**使用 RabbitMQ + 死信队列 (DLQ) 保证消息可靠:**

```java
@Service
public class InventoryEventPublisher {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 发布库存变更事件
     */
    public void publishStockChanged(Long skuId, Integer quantity) {
        StockChangedEvent event = new StockChangedEvent(skuId, quantity);

        // 设置消息持久化和确认机制
        rabbitTemplate.convertAndSend(
            "inventory.exchange",
            "stock.changed",
            event,
            message -> {
                message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                message.getMessageProperties().setExpiration("300000"); // 5分钟过期
                return message;
            }
        );
    }
}

@Component
@RabbitListener(queues = "inventory.stock.changed.queue")
public class StockChangedConsumer {

    /**
     * 消费库存变更事件 (幂等性保证)
     */
    @RabbitHandler
    public void handleStockChanged(StockChangedEvent event, Message message) {
        String messageId = message.getMessageProperties().getMessageId();

        // 幂等性检查
        if (redisTemplate.hasKey("processed:" + messageId)) {
            return; // 已处理,直接返回
        }

        try {
            // 处理业务逻辑
            doHandleStockChanged(event);

            // 标记已处理
            redisTemplate.opsForValue().set("processed:" + messageId, "1", 24, TimeUnit.HOURS);
        } catch (Exception e) {
            // 异常时抛出,消息会进入死信队列重试
            throw new BusinessException("处理库存变更失败", e);
        }
    }
}
```

### 4. 搜索功能最佳实践 (scm-product)

**使用 Elasticsearch 提供高性能搜索:**

```java
@Service
public class ProductSearchService {

    @Autowired
    private ElasticsearchRestTemplate elasticsearchTemplate;

    /**
     * 商品搜索 (支持中文分词、价格区间、排序)
     */
    public PageResult<ProductDocument> searchProducts(ProductSearchDTO dto) {
        // 构建查询条件
        BoolQueryBuilder query = QueryBuilders.boolQuery();

        // 关键词搜索 (中文分词)
        if (StringUtils.hasText(dto.getKeyword())) {
            query.should(QueryBuilders.matchQuery("productName", dto.getKeyword()).boost(2.0f));
            query.should(QueryBuilders.matchQuery("description", dto.getKeyword()));
        }

        // 价格区间
        if (dto.getMinPrice() != null || dto.getMaxPrice() != null) {
            RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery("price");
            if (dto.getMinPrice() != null) rangeQuery.gte(dto.getMinPrice());
            if (dto.getMaxPrice() != null) rangeQuery.lte(dto.getMaxPrice());
            query.filter(rangeQuery);
        }

        // 分类过滤
        if (dto.getCategoryId() != null) {
            query.filter(QueryBuilders.termQuery("categoryId", dto.getCategoryId()));
        }

        // 构建分页和排序
        Pageable pageable = PageRequest.of(
            dto.getPage() - 1,
            dto.getSize(),
            Sort.by(Sort.Direction.fromString(dto.getSortOrder()), dto.getSortField())
        );

        // 执行查询
        NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
            .withQuery(query)
            .withPageable(pageable)
            .withHighlightFields(new HighlightBuilder.Field("productName"))
            .build();

        SearchHits<ProductDocument> searchHits = elasticsearchTemplate.search(searchQuery, ProductDocument.class);

        // 转换结果
        return convertToPageResult(searchHits, pageable);
    }
}
```

### 5. 定时任务最佳实践

**使用 XXL-Job 实现分布式定时任务:**

```java
@Component
public class InventoryAlertJob {

    @Autowired
    private InventoryAlertService alertService;

    /**
     * 库存预警扫描任务
     *
     * XXL-Job 配置:
     * - 执行器: scm-inventory-executor
     * - 任务描述: 库存预警扫描
     * - Cron: 0 */5 * * * ? (每5分钟)
     * - 运行模式: BEAN
     * - JobHandler: inventoryAlertJob
     * - 路由策略: 轮询
     */
    @XxlJob("inventoryAlertJob")
    public void execute() throws Exception {
        XxlJobHelper.log("开始执行库存预警扫描...");

        long startTime = System.currentTimeMillis();
        int alertCount = 0;

        try {
            // 执行预警扫描 (分批处理)
            alertCount = alertService.scanAndAlert();

            long costTime = System.currentTimeMillis() - startTime;
            XxlJobHelper.log("库存预警扫描完成,触发预警 {} 条,耗时 {} ms", alertCount, costTime);
        } catch (Exception e) {
            XxlJobHelper.log("库存预警扫描失败: " + e.getMessage());
            throw e; // 抛出异常,XXL-Job 会记录失败日志
        }
    }
}

@Service
public class InventoryAlertService {

    /**
     * 扫描并发送预警 (分批处理)
     */
    public int scanAndAlert() {
        int alertCount = 0;
        int pageSize = 1000;
        int currentPage = 1;

        while (true) {
            // 分批查询库存
            List<InventoryStock> stocks = inventoryMapper.selectPage(
                new Page<>(currentPage, pageSize),
                Wrappers.<InventoryStock>lambdaQuery()
                    .orderByAsc(InventoryStock::getSkuId)
            ).getRecords();

            if (stocks.isEmpty()) {
                break;
            }

            // 检查并发送预警
            for (InventoryStock stock : stocks) {
                if (shouldAlert(stock)) {
                    sendAlert(stock);
                    alertCount++;
                }
            }

            currentPage++;
        }

        return alertCount;
    }
}
```

## 常见问题

**Q: 如何保证高并发场景下的库存一致性?**

A: 使用 Redis + Lua 脚本 + 异步数据库同步:
1. 所有库存操作先在 Redis 中执行 (Lua 脚本保证原子性)
2. 异步同步到 PostgreSQL (最终一致性)
3. 使用 Kafka 发送库存变更事件,触发下游服务更新

**Q: 分布式事务失败如何回滚?**

A: Seata AT 模式自动回滚:
1. Seata 在 undo_log 表中记录每次操作的回滚 SQL
2. 全局事务失败时,Seata TC 通知所有参与者回滚
3. 各服务执行 undo_log 中的回滚 SQL,恢复数据

**Q: Elasticsearch 数据如何与 PostgreSQL 保持同步?**

A: 使用 Canal 监听 PostgreSQL WAL:
1. Canal 伪装成 PostgreSQL 从库,订阅 binlog
2. 解析 binlog 中的 INSERT/UPDATE/DELETE 操作
3. 实时同步到 Elasticsearch
4. 定期全量同步校验数据一致性

**Q: 如何进行性能测试?**

A: 使用 JMeter 或 Gatling:
```bash
# JMeter 压测示例
jmeter -n -t inventory-deduct.jmx -l result.jtl -e -o report/

# 关键指标:
# - TPS (每秒事务数) >= 1000
# - 响应时间 P95 < 200ms
# - 错误率 < 0.1%
```

---

**文档版本:** v1.0
**适用服务:** scm-product, scm-inventory, scm-warehouse, scm-logistics, scm-supplier, scm-purchase
**最后更新:** 2025-12-29
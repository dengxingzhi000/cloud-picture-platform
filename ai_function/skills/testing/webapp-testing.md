---
name: scm-webapp-testing
description: SCM平台Web应用和API自动化测试技能。使用Playwright进行端到端测试，集成到Maven测试流程。覆盖API端点验证、前端功能测试、跨服务集成测试。支持多租户场景和分布式事务验证。
tools: Bash, Read, Write, Grep
model: inherit
---

# SCM Web 应用测试技能

为 SCM 供应链管理平台提供全面的 Web 应用和 API 自动化测试能力。

## 核心测试策略

### 测试金字塔

```
       /\
      /  \  E2E Tests (Playwright)
     /----\
    /      \  Integration Tests
   /--------\
  /  Unit    \  Unit Tests (JUnit 5)
 /------------\
```

**测试覆盖率要求**（根据服务类型）：

| 服务类型 | 单元测试 | 集成测试 | E2E测试 | 总覆盖率 |
|---------|---------|---------|---------|----------|
| Critical (order, finance) | ≥80% | ≥60% | ≥40% | ≥90% |
| Business | ≥70% | ≥50% | ≥30% | ≥80% |
| Foundation | ≥75% | ≥50% | ≥30% | ≥85% |
| Infrastructure | ≥80% | ≥60% | ≥40% | ≥90% |

## 1. API 端点测试

### REST API 测试（使用 Playwright）

```python
from playwright.sync_api import sync_playwright, expect
import json

def test_product_api_endpoints():
    """
    测试产品服务 API 端点

    测试目标: scm-product/service
    端口: 8201
    基础URL: http://localhost:8761/api/v1/products
    """

    with sync_playwright() as p:
        # 启动 API 请求上下文
        context = p.request.new_context(
            base_url="http://localhost:8761",
            extra_http_headers={
                "Authorization": f"Bearer {get_test_jwt_token()}",
                "X-Tenant-ID": "test_tenant_001"
            }
        )

        # 测试 1: 创建产品
        create_response = context.post(
            "/api/v1/products",
            data={
                "productName": "Test Product",
                "categoryId": 1,
                "brandId": 1,
                "price": "99.99",
                "stock": 100
            }
        )

        assert create_response.ok
        product_data = create_response.json()
        assert product_data["code"] == 200
        assert product_data["data"]["productName"] == "Test Product"

        product_id = product_data["data"]["id"]

        # 测试 2: 查询产品
        get_response = context.get(f"/api/v1/products/{product_id}")
        assert get_response.ok

        get_data = get_response.json()
        assert get_data["data"]["id"] == product_id

        # 测试 3: 更新产品
        update_response = context.put(
            f"/api/v1/products/{product_id}",
            data={"price": "89.99"}
        )
        assert update_response.ok

        # 测试 4: 删除产品
        delete_response = context.delete(f"/api/v1/products/{product_id}")
        assert delete_response.ok

        # 验证删除成功
        verify_response = context.get(f"/api/v1/products/{product_id}")
        assert verify_response.status == 404


def get_test_jwt_token() -> str:
    """
    获取测试用 JWT Token

    调用认证服务登录接口
    """
    with sync_playwright() as p:
        context = p.request.new_context(base_url="http://localhost:8761")

        response = context.post(
            "/api/v1/auth/login",
            data={
                "username": "test_user",
                "password": "test_password"
            }
        )

        assert response.ok
        data = response.json()
        return data["data"]["token"]
```

### 多租户隔离验证

```python
def test_multi_tenant_data_isolation():
    """
    验证多租户数据隔离

    场景:
    - 租户 A 创建订单
    - 租户 B 不能查询到租户 A 的订单
    """

    with sync_playwright() as p:
        # 租户 A
        tenant_a_context = p.request.new_context(
            base_url="http://localhost:8761",
            extra_http_headers={
                "Authorization": f"Bearer {get_jwt_for_tenant('tenant_a')}",
                "X-Tenant-ID": "tenant_a"
            }
        )

        # 租户 A 创建订单
        create_response = tenant_a_context.post(
            "/api/v1/orders",
            data={
                "customerId": 1,
                "items": [{"skuId": 1, "quantity": 10}]
            }
        )

        assert create_response.ok
        order_data = create_response.json()
        order_id = order_data["data"]["orderId"]

        # 租户 B
        tenant_b_context = p.request.new_context(
            base_url="http://localhost:8761",
            extra_http_headers={
                "Authorization": f"Bearer {get_jwt_for_tenant('tenant_b')}",
                "X-Tenant-ID": "tenant_b"
            }
        )

        # 租户 B 尝试查询租户 A 的订单（应失败）
        get_response = tenant_b_context.get(f"/api/v1/orders/{order_id}")

        assert get_response.status == 403  # Forbidden
        error_data = get_response.json()
        assert "租户权限不足" in error_data["message"]
```

### API 性能测试

```python
import time
from concurrent.futures import ThreadPoolExecutor

def test_api_performance_under_load():
    """
    API 性能压测

    指标:
    - 吞吐量 (TPS)
    - 平均响应时间
    - P99 响应时间
    - 错误率
    """

    def make_request():
        with sync_playwright() as p:
            context = p.request.new_context(
                base_url="http://localhost:8761",
                extra_http_headers={
                    "Authorization": f"Bearer {get_test_jwt_token()}"
                }
            )

            start_time = time.time()
            response = context.get("/api/v1/products?page=1&size=20")
            end_time = time.time()

            return {
                "success": response.ok,
                "response_time": (end_time - start_time) * 1000  # ms
            }

    # 并发 100 个请求
    with ThreadPoolExecutor(max_workers=100) as executor:
        results = list(executor.map(lambda _: make_request(), range(100)))

    # 统计结果
    success_count = sum(1 for r in results if r["success"])
    response_times = [r["response_time"] for r in results]

    avg_response_time = sum(response_times) / len(response_times)
    p99_response_time = sorted(response_times)[98]  # P99

    print(f"成功率: {success_count/100*100}%")
    print(f"平均响应时间: {avg_response_time:.2f}ms")
    print(f"P99响应时间: {p99_response_time:.2f}ms")

    # 断言性能要求
    assert success_count == 100, "请求失败率过高"
    assert avg_response_time < 200, "平均响应时间超过 200ms"
    assert p99_response_time < 500, "P99 响应时间超过 500ms"
```

## 2. 分布式事务测试

### Seata 事务回滚验证

```python
def test_seata_global_transaction_rollback():
    """
    测试分布式事务回滚

    场景: 创建订单流程
    1. scm-order: 创建订单
    2. scm-inventory: 扣减库存
    3. scm-finance: 创建支付记录
    4. 模拟支付失败 → 全局回滚

    期望:
    - 订单未创建
    - 库存未扣减
    - 无支付记录
    """

    with sync_playwright() as p:
        context = p.request.new_context(
            base_url="http://localhost:8761",
            extra_http_headers={
                "Authorization": f"Bearer {get_test_jwt_token()}",
                "X-Tenant-ID": "test_tenant"
            }
        )

        # 记录操作前的状态
        initial_stock = get_stock_quantity(context, sku_id=1)
        initial_order_count = get_order_count(context)

        # 创建订单（强制支付失败）
        response = context.post(
            "/api/v1/orders",
            data={
                "customerId": 1,
                "items": [{"skuId": 1, "quantity": 10}],
                "forcePaymentFailure": True  # 测试参数
            }
        )

        # 请求应失败
        assert response.status == 500
        error_data = response.json()
        assert "分布式事务回滚" in error_data["message"]

        # 验证回滚效果
        final_stock = get_stock_quantity(context, sku_id=1)
        final_order_count = get_order_count(context)

        assert final_stock == initial_stock, "库存未正确回滚"
        assert final_order_count == initial_order_count, "订单未正确回滚"
```

### 幂等性测试

```python
def test_inventory_deduction_idempotency():
    """
    测试库存扣减幂等性

    场景:
    - 使用相同 requestId 多次扣减库存
    - 应只扣减一次

    验证:
    - 第二次请求返回成功但不扣减
    - 库存数量正确
    """

    request_id = str(uuid.uuid4())

    with sync_playwright() as p:
        context = p.request.new_context(
            base_url="http://localhost:8761",
            extra_http_headers={
                "Authorization": f"Bearer {get_test_jwt_token()}",
                "X-Tenant-ID": "test_tenant"
            }
        )

        initial_stock = get_stock_quantity(context, sku_id=1)

        # 第一次扣减
        response1 = context.post(
            "/api/v1/inventory/deduct",
            data={
                "skuId": 1,
                "quantity": 10,
                "requestId": request_id
            }
        )
        assert response1.ok

        # 第二次扣减（相同 requestId）
        response2 = context.post(
            "/api/v1/inventory/deduct",
            data={
                "skuId": 1,
                "quantity": 10,
                "requestId": request_id
            }
        )
        assert response2.ok  # 幂等返回成功

        # 验证库存只扣减一次
        final_stock = get_stock_quantity(context, sku_id=1)
        assert final_stock == initial_stock - 10, "幂等性失败，库存扣减了两次"
```

## 3. UI 端到端测试

### 登录流程测试

```python
def test_user_login_flow():
    """
    测试用户登录流程

    步骤:
    1. 访问登录页
    2. 输入用户名密码
    3. 点击登录
    4. 验证跳转到首页
    5. 验证用户信息显示
    """

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        context = browser.new_context()
        page = context.new_page()

        # 访问登录页
        page.goto("http://localhost:8761/login")

        # 等待页面加载
        page.wait_for_load_state('networkidle')

        # 输入用户名密码
        page.fill('input[name="username"]', 'test_user')
        page.fill('input[name="password"]', 'test_password')

        # 点击登录按钮
        page.click('button[type="submit"]')

        # 等待跳转
        page.wait_for_url("http://localhost:8761/dashboard")

        # 验证用户信息显示
        expect(page.locator('.user-info')).to_contain_text('test_user')

        # 截图（用于调试）
        page.screenshot(path='/tmp/login_success.png')

        browser.close()
```

### 订单创建流程测试

```python
def test_order_creation_flow():
    """
    端到端测试: 完整订单创建流程

    步骤:
    1. 登录
    2. 搜索商品
    3. 添加到购物车
    4. 结算
    5. 填写收货地址
    6. 选择支付方式
    7. 提交订单
    8. 验证订单创建成功
    """

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=False, slow_mo=100)
        page = browser.new_page()

        # 1. 登录
        login(page)

        # 2. 搜索商品
        page.goto("http://localhost:8761/products")
        page.fill('input[placeholder="搜索商品"]', 'iPhone')
        page.click('button:has-text("搜索")')

        page.wait_for_load_state('networkidle')

        # 3. 选择商品并添加到购物车
        page.click('.product-item:first-child')
        page.click('button:has-text("加入购物车")')

        # 等待加购成功提示
        expect(page.locator('.toast')).to_contain_text('加入购物车成功')

        # 4. 去结算
        page.click('a:has-text("购物车")')
        page.click('button:has-text("去结算")')

        # 5. 填写收货地址
        page.fill('input[name="consignee"]', '测试用户')
        page.fill('input[name="phone"]', '13800138000')
        page.fill('textarea[name="address"]', '北京市朝阳区测试街道1号')

        # 6. 选择支付方式
        page.click('label:has-text("支付宝")')

        # 7. 提交订单
        page.click('button:has-text("提交订单")')

        # 8. 验证订单创建成功
        page.wait_for_selector('.order-success')
        expect(page.locator('.order-success')).to_be_visible()

        # 提取订单号
        order_no = page.locator('.order-no').inner_text()
        print(f"订单创建成功: {order_no}")

        # 截图保存
        page.screenshot(path=f'/tmp/order_{order_no}.png')

        browser.close()
```

## 4. 集成到 Maven 测试流程

### pom.xml 配置

```xml
<build>
    <plugins>
        <!-- Surefire for Unit Tests -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <version>3.0.0</version>
            <configuration>
                <includes>
                    <include>**/*Test.java</include>
                </includes>
            </configuration>
        </plugin>

        <!-- Failsafe for Integration Tests -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-failsafe-plugin</artifactId>
            <version>3.0.0</version>
            <executions>
                <execution>
                    <goals>
                        <goal>integration-test</goal>
                        <goal>verify</goal>
                    </goals>
                </execution>
            </executions>
            <configuration>
                <includes>
                    <include>**/*IT.java</include>
                </includes>
            </configuration>
        </plugin>

        <!-- JaCoCo for Coverage -->
        <plugin>
            <groupId>org.jacoco</groupId>
            <artifactId>jacoco-maven-plugin</artifactId>
            <version>0.8.10</version>
            <executions>
                <execution>
                    <goals>
                        <goal>prepare-agent</goal>
                    </goals>
                </execution>
                <execution>
                    <id>report</id>
                    <phase>test</phase>
                    <goals>
                        <goal>report</goal>
                    </goals>
                </execution>
                <execution>
                    <id>check</id>
                    <goals>
                        <goal>check</goal>
                    </goals>
                    <configuration>
                        <rules>
                            <rule>
                                <element>PACKAGE</element>
                                <limits>
                                    <limit>
                                        <counter>LINE</counter>
                                        <value>COVEREDRATIO</value>
                                        <minimum>0.80</minimum> <!-- 80% 覆盖率 -->
                                    </limit>
                                </limits>
                            </rule>
                        </rules>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### 运行测试

```bash
# 运行所有测试
mvn clean verify

# 只运行单元测试
mvn test

# 只运行集成测试
mvn verify -DskipUnitTests

# 运行 Playwright 测试（需要 Python）
python3 /tests/playwright/run_all_tests.py

# 生成覆盖率报告
mvn jacoco:report
# 报告位置: target/site/jacoco/index.html
```

## 5. 测试数据管理

### 使用 Testcontainers

```java
@Testcontainers
@SpringBootTest
public class OrderServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14")
        .withDatabaseName("test_db_order")
        .withUsername("test")
        .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", redis::getFirstMappedPort);
    }

    @Test
    void testCreateOrder() {
        // 测试逻辑
    }
}
```

### 测试数据初始化

```sql
-- /src/test/resources/test-data.sql

-- 清理数据
TRUNCATE TABLE ord_order CASCADE;
TRUNCATE TABLE inv_stock CASCADE;

-- 插入测试数据
INSERT INTO inv_stock (sku_id, warehouse_id, available_stock, tenant_id)
VALUES (1, 1, 1000, 'test_tenant');

INSERT INTO prod_sku (id, product_name, price, tenant_id)
VALUES (1, 'Test Product', 99.99, 'test_tenant');
```

## 6. CI/CD 集成

### GitHub Actions 工作流

```yaml
name: SCM Platform Tests

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  test:
    runs-on: ubuntu-latest

    services:
      postgres:
        image: postgres:14
        env:
          POSTGRES_PASSWORD: postgres
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5

      redis:
        image: redis:7-alpine
        options: >-
          --health-cmd "redis-cli ping"
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5

    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Run Unit Tests
        run: mvn test

      - name: Run Integration Tests
        run: mvn verify

      - name: Install Python Dependencies
        run: |
          pip install playwright pytest
          playwright install chromium

      - name: Run Playwright Tests
        run: python3 tests/playwright/run_all_tests.py

      - name: Upload Coverage Report
        uses: codecov/codecov-action@v3
        with:
          file: ./target/site/jacoco/jacoco.xml

      - name: Check Coverage Threshold
        run: |
          coverage=$(cat target/site/jacoco/index.html | grep -oP 'Total.*?(\d+)%' | tail -1 | grep -oP '\d+')
          if [ $coverage -lt 80 ]; then
            echo "Coverage $coverage% is below threshold 80%"
            exit 1
          fi
```

## 测试最佳实践

1. **测试隔离**: 每个测试用例独立，不依赖其他测试
2. **数据清理**: 测试前后清理数据，避免污染
3. **幂等性**: 测试可重复执行，结果一致
4. **快速反馈**: 单元测试 < 10s，集成测试 < 5min
5. **有意义的断言**: 验证业务逻辑，不只是检查非空
6. **测试命名**: 清晰描述测试场景和预期结果

## 常见问题

**Q: Playwright 测试在 CI 环境失败？**
A: 确保安装了浏览器依赖：
```bash
playwright install --with-deps chromium
```

**Q: 分布式事务测试不稳定？**
A: 增加 Seata 超时配置：
```yaml
seata:
  client:
    tm:
      default-global-transaction-timeout: 120000  # 2分钟
```

**Q: 测试覆盖率如何排除生成的代码？**
A: JaCoCo 配置：
```xml
<configuration>
    <excludes>
        <exclude>**/dto/**</exclude>
        <exclude>**/mapper/**</exclude>
        <exclude>**/*Application.class</exclude>
    </excludes>
</configuration>
```

## 扩展阅读

- `.claude/agents/workflow-executor.md` - 工作流验证
- `CLAUDE.md` - 构建和测试命令
- Playwright 文档: https://playwright.dev/python/
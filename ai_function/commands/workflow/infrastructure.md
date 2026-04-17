# SCM 基础设施代码提交流程 (Infrastructure)

适用于 SCM 平台的基础设施模块:
- **scm-gateway** (API 网关 - Spring Cloud Gateway)
- **scm-common** (公共模块 - 工具类、安全、数据访问)
- **docker-compose.yml** (基础设施编排)
- **scripts/** (数据库脚本、部署脚本)
- **pom.xml** (父 POM 依赖管理)

## 准备工作

1. **暂存区确认:** 已将所有相关的代码变更添加到 `git` 暂存区
2. **禁止变更:** 此阶段严禁对暂存区进行任何修改
3. **梳理总结:** 使用 `git status --short` 和 `git diff --cached` 阅读暂存区内容

## 基础设施特定检查

基础设施变更影响**所有服务**,必须格外谨慎,充分测试。

### 1. 网关服务检查 (scm-gateway)

#### 构建验证

```bash
cd scm-gateway
mvn clean verify
```

#### 路由配置检查

**关键检查点:**
- [ ] 路由规则正确 (URI、Predicate、Filter)
- [ ] 负载均衡策略合理
- [ ] 超时配置适当 (避免级联超时)
- [ ] 重试策略合理 (避免雪崩)

**示例配置审查:**
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: scm-product-service
          uri: lb://scm-product  # ✅ 使用负载均衡
          predicates:
            - Path=/api/v1/products/**
          filters:
            - StripPrefix=2
            - name: Retry
              args:
                retries: 3  # ⚠️ 重试次数不宜过大
                methods: GET  # ✅ 只对幂等方法重试
```

#### 安全配置检查

**强制要求:**
- [ ] API 签名验证已启用
- [ ] IP 访问控制配置正确
- [ ] 跨域 (CORS) 配置合理
- [ ] 敏感路径需认证 (JWT)

**安全配置示例:**
```yaml
gateway:
  security:
    api-signature:
      enabled: true  # ✅ 必须启用
      secret: ${API_SECRET_WEB_APP}  # ✅ 使用环境变量
    ip-access-control:
      enabled: true
      whitelist:
        - 10.0.0.0/8  # 内网
        - ${ADMIN_IP}  # 管理员 IP
```

#### 限流配置检查

**防止服务过载:**
- [ ] 全局限流配置 (QPS)
- [ ] 单 IP 限流配置 (防刷)
- [ ] 敏感接口特殊限流 (登录、支付等)

**Sentinel 配置示例:**
```yaml
spring:
  cloud:
    sentinel:
      datasource:
        flow:
          nacos:
            server-addr: ${NACOS_SERVER}
            dataId: gateway-flow-rules
            groupId: SENTINEL_GROUP
            rule-type: flow
```

#### 网关集成测试

```bash
# 启动网关和至少一个后端服务
docker-compose up -d nacos gateway product-service

# 测试路由
curl http://localhost:8761/api/v1/products

# 测试限流
for i in {1..100}; do curl http://localhost:8761/api/v1/products; done

# 测试认证
curl -H "Authorization: Bearer invalid-token" http://localhost:8761/api/v1/users
```

### 2. 公共模块检查 (scm-common)

#### 模块构建验证

```bash
cd scm-common
mvn clean install -DskipTests=false
```

**scm-common 包含多个子模块:**
- `core` - 核心工具类
- `security-api` - 安全接口
- `data` - 数据访问层
- `web` - Web 安全过滤器
- `monitoring` - 监控集成
- `integration` - 消息集成

**关键检查:**
- [ ] 子模块版本一致
- [ ] 依赖版本无冲突
- [ ] 接口变更向后兼容

#### 依赖变更影响分析

**公共模块依赖变更会影响所有服务!**

```bash
# 检查依赖变更
git diff master -- scm-common/pom.xml

# 检查哪些服务依赖了 scm-common
grep -r "scm-common" */pom.xml
```

**依赖升级检查清单:**
- [ ] Spring Boot 版本升级 → **影响所有服务**
- [ ] MyBatis-Plus 版本升级 → **影响所有数据访问**
- [ ] Redis 客户端版本升级 → **影响缓存功能**
- [ ] Seata 版本升级 → **影响分布式事务**

**验证策略:**
1. 先在一个小服务上验证 (如 scm-notify)
2. 观察运行稳定性 (1-2天)
3. 逐步推广到其他服务

#### API 兼容性检查

**公共工具类/接口变更必须向后兼容:**

```java
// ❌ 错误: 删除或修改现有方法签名
public class UUIDv7Util {
    // public static String generate() { ... }  // ❌ 删除会导致编译失败
    public static UUID generateUUID() { ... }  // ❌ 改名会导致编译失败
}

// ✅ 正确: 保留旧方法,添加新方法
public class UUIDv7Util {
    public static String generate() { ... }  // ✅ 保留

    @Deprecated
    public static String generateString() { ... }  // ✅ 废弃但保留

    public static UUID generateUUID() { ... }  // ✅ 新增
}
```

#### 单元测试 (覆盖率 >= 90%)

公共模块被广泛使用,**必须保证极高质量:**

```bash
mvn test jacoco:report

# 每个子模块都要检查覆盖率
# scm-common/core/target/site/jacoco/index.html
# scm-common/data/target/site/jacoco/index.html
```

### 3. 数据库脚本检查 (scripts/db/)

#### 脚本语法检查

```bash
# PostgreSQL 语法检查
cd scripts/db/microservices
psql -U postgres --dry-run -f 001_db_user.sql
```

#### 迁移脚本规范

**迁移脚本必须:**
- [ ] 幂等性 (可重复执行)
- [ ] 向后兼容 (不破坏现有数据)
- [ ] 有回滚脚本 (如需要)

**示例:**
```sql
-- ✅ 幂等性: 使用 IF NOT EXISTS
CREATE TABLE IF NOT EXISTS t_new_table (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    ...
);

-- ✅ 向后兼容: 新增列使用默认值
ALTER TABLE t_existing_table
ADD COLUMN IF NOT EXISTS new_column VARCHAR(255) DEFAULT 'default_value';

-- ❌ 不向后兼容: 删除列 (除非确认无使用)
-- ALTER TABLE t_existing_table DROP COLUMN old_column;
```

#### 分区表检查

**PostgreSQL 分区表约束:**
- [ ] UNIQUE 约束包含分区键
- [ ] 示例: `UNIQUE (order_no, create_time)` 而非 `UNIQUE (order_no)`

```sql
-- ✅ 正确: 包含分区键
CREATE TABLE ord_order (
    id UUID DEFAULT gen_random_uuid(),
    order_no VARCHAR(128) NOT NULL,
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_order_no_create_time UNIQUE (order_no, create_time)
) PARTITION BY RANGE (create_time);

-- ❌ 错误: 缺少分区键
CREATE TABLE ord_order (
    id UUID DEFAULT gen_random_uuid(),
    order_no VARCHAR(128) NOT NULL UNIQUE,  -- ❌ PostgreSQL 不允许
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW()
) PARTITION BY RANGE (create_time);
```

### 4. Docker Compose 检查

#### 配置验证

```bash
# 验证 docker-compose.yml 语法
docker-compose config

# 检查端口冲突
docker-compose config --services | xargs -I {} docker-compose port {}
```

#### 资源限制检查

**生产环境必须设置资源限制:**

```yaml
services:
  postgres:
    image: postgres:14
    deploy:
      resources:
        limits:
          cpus: '2.0'  # ✅ 限制 CPU
          memory: 4G   # ✅ 限制内存
        reservations:
          cpus: '1.0'
          memory: 2G
```

#### 数据持久化检查

**关键服务必须持久化数据:**

```yaml
services:
  postgres:
    volumes:
      - postgres_data:/var/lib/postgresql/data  # ✅ 持久化

  redis:
    volumes:
      - redis_data:/data  # ✅ 持久化

volumes:
  postgres_data:
  redis_data:
```

### 5. 父 POM 依赖管理检查

#### 依赖版本一致性

```bash
# 检查父 POM 变更
git diff master -- pom.xml

# 检查版本属性
grep "<.*\.version>" pom.xml
```

**版本管理原则:**
- [ ] 所有子模块使用统一版本 (继承父 POM)
- [ ] 第三方依赖版本集中管理
- [ ] 避免子模块覆盖父 POM 版本

**示例:**
```xml
<!-- ✅ 正确: 父 POM 统一管理 -->
<properties>
    <spring-boot.version>4.0.0</spring-boot.version>
    <spring-cloud.version>2025.1.0</spring-cloud.version>
    <mybatis-plus.version>3.5.15</mybatis-plus.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>${spring-boot.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

#### 插件版本检查

```xml
<build>
    <pluginManagement>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>  <!-- ✅ 锁定版本 -->
                <configuration>
                    <release>21</release>
                </configuration>
            </plugin>
        </plugins>
    </pluginManagement>
</build>
```

## 标准流程

### 1. 创建 Issue

```bash
gh issue create \
  --title "chore(gateway): 升级Spring Cloud Gateway至2025.1.0" \
  --body "$(cat <<EOF
## 背景
当前使用的 Spring Cloud Gateway 2024.x 存在已知漏洞 (CVE-2024-xxxx),需升级至 2025.1.0。

## 变更说明
- 升级 Spring Cloud Gateway: 2024.0.0 → 2025.1.0
- 升级 Spring Boot: 3.3.0 → 4.0.0
- 适配新版本 API 变更

## 影响范围
- **所有服务**: 通过父 POM 继承,影响所有子模块
- **网关服务**: 需要适配配置变更
- **认证服务**: 可能需要调整 JWT 集成

## 兼容性评估
- ✅ 向后兼容 API
- ⚠️ 配置格式有变更 (需迁移)
- ⚠️ 部分过时 API 已删除

## 测试计划
1. 本地环境完整回归测试
2. 集成测试全量通过
3. 预发布环境灰度验证 (1天)
4. 生产环境分批发布

## 回滚方案
- 保留旧版本镜像
- 数据库无变更,可直接回滚
- 预计回滚时间: < 10分钟

## 相关文档
- Spring Cloud 2025.1.0 Release Notes
- 迁移指南: docs/guides/SPRING_CLOUD_2025_MIGRATION.md
EOF
)" \
  --label "type/chore,priority/P1,domain/infra,breaking-change"
```

**Issue 内容要求:**
- 必须包含**影响范围**分析
- 必须包含**兼容性评估**
- 必须包含**测试计划**
- 必须包含**回滚方案**

### 2. 创建特性分支

```bash
git checkout master
git pull origin master

# 基础设施变更使用 chore 类型
git checkout -b chore/gateway-upgrade-2025-#123
```

### 3. 提交变更

```bash
git commit
```

**Commit Message 示例:**

```
chore(gateway): 升级Spring Cloud Gateway至2025.1.0

- 升级Spring Boot 3.3.0 → 4.0.0
- 升级Spring Cloud Gateway 2024.0.0 → 2025.1.0
- 适配新版本配置格式变更
- 更新路由配置语法
- 更新限流配置 (Sentinel 集成)
- 调整 JWT 过滤器配置

配置变更:
- spring.cloud.gateway.routes → 新格式
- 移除已废弃的 spring.cloud.gateway.default-filters
- 新增 spring.cloud.gateway.metrics.enabled

测试情况:
- 单元测试通过: 150/150
- 集成测试通过: 45/45
- 本地环境回归测试通过

影响范围:
- 所有通过网关访问的服务
- 需同步升级所有微服务依赖

BREAKING CHANGE: 配置格式变更,需迁移旧配置

Refs #123
```

### 4. 推送代码并创建 PR

```bash
git push -u origin chore/gateway-upgrade-2025-#123

gh pr create \
  --title "chore(gateway): 升级Spring Cloud Gateway至2025.1.0" \
  --body "$(cat <<EOF
## 变更说明
升级 Spring Cloud Gateway 至 2025.1.0,修复安全漏洞并使用新特性。

## 主要变更
- Spring Boot 4.0.0
- Spring Cloud Gateway 2025.1.0
- 配置格式迁移

## 测试说明
- [x] 单元测试全量通过 (150/150)
- [x] 集成测试全量通过 (45/45)
- [x] 本地环境回归测试通过
- [ ] 预发布环境验证 (待部署)

## 影响范围
⚠️ **所有服务** - 通过父 POM 继承

## 破坏性变更
- 配置格式变更 (已提供迁移脚本)
- 部分过时 API 删除 (已适配)

## 部署计划
1. 预发布环境验证 (1天)
2. 生产环境灰度发布 (网关 10% → 50% → 100%)
3. 逐步升级微服务 (每天 2-3 个服务)

## 回滚方案
- 保留旧版本 Docker 镜像
- 无数据库变更,可快速回滚
- 预计回滚时间: < 10分钟

## 迁移文档
- docs/guides/SPRING_CLOUD_2025_MIGRATION.md

Refs #123
EOF
)" \
  --label "type/chore,priority/P1,domain/infra,breaking-change"
```

### 5. 代码审查

**审查者特殊关注点 (基础设施):**
- [ ] 影响范围评估准确
- [ ] 向后兼容性或迁移方案
- [ ] 测试覆盖充分 (单元 + 集成)
- [ ] 部署计划合理 (分批、灰度)
- [ ] 回滚方案可行
- [ ] 依赖版本无冲突
- [ ] 配置变更文档完整

**强制要求:**
- 至少 **3 名审查者** (架构师、安全、运维)
- **架构师必须批准**

### 6. 合并代码

**合并前检查清单:**
- [ ] 所有 CI/CD 检查通过
- [ ] 至少 3 名审查者批准 (包括架构师)
- [ ] 预发布环境验证通过
- [ ] 部署文档和回滚文档已准备
- [ ] 相关团队已通知 (影响评估会议)

**合并后步骤:**
1. 在预发布环境验证 1-2 天
2. 生产环境分批灰度发布
3. 监控关键指标 (QPS、延迟、错误率)
4. 确认稳定后,逐步推广

## GitHub Labels 规范

**必需标签:**
- **优先级:** `priority/P0` ~ `priority/P3`
- **类型:** `type/chore` (基础设施变更通常使用 chore)
- **领域:** `domain/infra`, `domain/gateway`, `domain/common`

**特殊标签:**
- `breaking-change` - 破坏性变更 (**强制标注**)
- `dependency-upgrade` - 依赖升级
- `security-patch` - 安全补丁 (优先级 P0)
- `database-migration` - 数据库迁移
- `requires-deployment-plan` - 需要部署计划

## 最佳实践

### 1. 依赖升级策略

**小版本升级 (如 3.3.0 → 3.3.1):**
- 通常只修复 Bug,风险较小
- 回归测试即可

**中版本升级 (如 3.3.x → 3.4.0):**
- 可能包含新特性和 API 变更
- 需要仔细阅读 Release Notes
- 充分测试新特性和变更点

**大版本升级 (如 3.x → 4.0):**
- 通常包含破坏性变更
- **必须先在小范围验证**
- 准备详细迁移文档
- 分批灰度发布

### 2. 网关限流配置

**全局限流 (保护后端服务):**

```yaml
spring:
  cloud:
    gateway:
      default-filters:
        - name: RequestRateLimiter
          args:
            redis-rate-limiter:
              replenishRate: 1000  # 每秒补充令牌数
              burstCapacity: 2000  # 令牌桶容量
              requestedTokens: 1   # 每个请求消耗令牌数
```

**单 IP 限流 (防刷):**

```java
@Configuration
public class GatewayConfig {

    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(
            exchange.getRequest()
                .getRemoteAddress()
                .getAddress()
                .getHostAddress()
        );
    }
}
```

### 3. 网关熔断配置

**使用 Sentinel 实现熔断:**

```yaml
spring:
  cloud:
    sentinel:
      transport:
        dashboard: localhost:8080
      datasource:
        degrade:
          nacos:
            server-addr: ${NACOS_SERVER}
            dataId: gateway-degrade-rules
            groupId: SENTINEL_GROUP
            rule-type: degrade
```

**降级规则示例:**
```json
[
  {
    "resource": "scm-order-service",
    "grade": 0,
    "count": 100,
    "timeWindow": 10,
    "minRequestAmount": 5,
    "statIntervalMs": 1000
  }
]
```

### 4. 公共模块版本管理

**使用 Maven Release Plugin:**

```bash
# 准备发布 (自动更新版本号)
mvn release:prepare

# 执行发布
mvn release:perform

# 回滚发布
mvn release:rollback
```

**版本号规范:**
- `1.0.0-SNAPSHOT` - 开发版本
- `1.0.0` - 正式版本
- `1.0.1` - Bug 修复版本
- `1.1.0` - 新特性版本
- `2.0.0` - 破坏性变更版本

## 常见问题

**Q: 父 POM 依赖升级后,某个服务构建失败怎么办?**

A: 排查依赖冲突:
```bash
# 查看依赖树
cd scm-problem-service
mvn dependency:tree

# 查看冲突
mvn dependency:tree -Dverbose

# 排除冲突依赖
<dependency>
    <groupId>xxx</groupId>
    <artifactId>yyy</artifactId>
    <exclusions>
        <exclusion>
            <groupId>conflict-group</groupId>
            <artifactId>conflict-artifact</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

**Q: 网关配置变更如何灰度发布?**

A: 使用 Nacos 配置中心动态更新:
1. 在 Nacos 中修改配置
2. 网关自动刷新配置 (无需重启)
3. 观察监控指标
4. 确认无问题后保存

**Q: 数据库迁移脚本如何回滚?**

A: 准备回滚脚本:
```sql
-- 迁移脚本: V1.2__add_new_column.sql
ALTER TABLE t_user ADD COLUMN new_column VARCHAR(255);

-- 回滚脚本: R1.2__rollback_add_new_column.sql
ALTER TABLE t_user DROP COLUMN IF EXISTS new_column;
```

**Q: Docker Compose 服务无法启动怎么排查?**

A: 逐步排查:
```bash
# 1. 检查配置语法
docker-compose config

# 2. 查看服务日志
docker-compose logs service-name

# 3. 检查端口占用
netstat -tuln | grep PORT

# 4. 检查磁盘空间
df -h

# 5. 检查内存
free -h
```

---

**文档版本:** v1.0
**适用范围:** scm-gateway, scm-common, docker-compose, scripts, 父 POM
**最后更新:** 2025-12-29
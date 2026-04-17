# SCM 基础服务代码提交流程 (Foundation Services)

适用于 SCM 平台的基础服务模块:
- **scm-auth** (认证服务)
- **scm-system** (系统服务 - 用户/部门/角色/权限)
- **scm-tenant** (多租户管理)
- **scm-approval** (审批流程)
- **scm-audit** (审计日志)
- **scm-notify** (通知服务)

## 准备工作

1. **暂存区确认:** 已将所有相关的代码变更添加到 `git` 暂存区 (staging area)
2. **禁止变更:** 此阶段严禁对暂存区进行任何修改
3. **梳理总结:** 使用 `git status --short` 和 `git diff --cached` 阅读暂存区内容

## 基础服务特定检查

基础服务是整个平台的核心依赖,**必须保证极高的稳定性和安全性**。

### 1. 构建验证

```bash
# 进入对应服务目录
cd scm-auth  # 或 scm-system, scm-tenant 等

# 构建验证 (必须通过)
mvn clean verify -DskipTests=false
```

### 2. 安全性检查 (强制)

基础服务涉及认证、授权、审计等核心安全功能,**安全检查不可跳过**:

```bash
# 依赖安全扫描
mvn dependency-check:check

# 确认无高危漏洞 (CVSS >= 7.0)
```

**特殊检查:**
- [ ] JWT 密钥配置检查 (scm-auth)
- [ ] 权限验证逻辑无漏洞 (scm-system)
- [ ] 租户隔离完整性 (scm-tenant)
- [ ] 审计日志完整性 (scm-audit)
- [ ] 密码/密钥不在代码中硬编码

### 3. 数据库多租户检查

基础服务使用多数据库架构,**必须确保 @DS 注解正确**:

```bash
# 检查数据源注解
grep -r "@DS" scm-system/service/src/main/java/

# 确认:
# - 用户相关 → @DS("user")
# - 组织相关 → @DS("org")
# - 权限相关 → @DS("permission")
# - 审批相关 → @DS("approval")
# - 审计相关 → @DS("audit")
```

### 4. 单元测试 (覆盖率 >= 85%)

基础服务要求更高的测试覆盖率:

```bash
mvn test jacoco:report

# 检查覆盖率报告
# target/site/jacoco/index.html
```

**关键测试场景:**
- [ ] 认证失败场景 (scm-auth)
- [ ] 权限拒绝场景 (scm-system)
- [ ] 租户隔离验证 (scm-tenant)
- [ ] 审计日志记录完整性 (scm-audit)

### 5. API 兼容性检查

基础服务的 API 被其他所有服务依赖,**不能轻易破坏兼容性**:

- [ ] 未删除或修改现有公共 API
- [ ] 如有破坏性变更,必须在 Commit Message 中标记 `BREAKING CHANGE:`
- [ ] 检查 Dubbo API 接口变更 (api 模块)

### 6. 集成测试

```bash
# 启动依赖基础设施
docker-compose up -d nacos postgres redis

# 运行集成测试
mvn verify -P integration-test
```

## 标准流程

### 1. 创建 Issue

```bash
gh issue create \
  --title "feat(auth): 添加WebAuthn无密码认证支持" \
  --body "$(cat <<EOF
## 背景
用户需要更安全便捷的认证方式,WebAuthn提供生物识别和硬件密钥支持。

## 需求描述
- 实现 WebAuthn 注册流程
- 实现 WebAuthn 认证流程
- 支持多设备管理

## 技术方案
- 使用 Yubico WebAuthn Server 库
- 数据库存储凭据公钥
- 兼容现有 JWT 流程

## 验收标准
- [ ] WebAuthn 注册成功率 >= 95%
- [ ] 认证响应时间 < 200ms
- [ ] 单元测试覆盖率 >= 85%
- [ ] 安全审计通过

## 安全影响评估
- 涉及认证流程变更,需安全团队审查
- 需更新安全文档

## 相关文档
- WebAuthn 规范: https://www.w3.org/TR/webauthn/
- 架构设计文档: docs/design/WEBAUTHN_DESIGN.md
EOF
)" \
  --label "type/feature,priority/P1,domain/auth,security-review"
```

**Issue 内容要求:**
- 必须包含**安全影响评估**章节
- 必须添加 `security-review` 标签(如涉及认证授权)
- 必须关联相关设计文档

### 2. 创建特性分支

```bash
# 更新主分支
git checkout master
git pull origin master

# 创建特性分支 (包含服务名)
git checkout -b feat/auth-webauthn-#123
```

**分支命名格式:** `<type>/<service>-<short-desc>-#<issue>`

### 3. 提交变更

```bash
git commit
```

**Commit Message 示例:**

```
feat(auth): 添加WebAuthn无密码认证支持

- 实现WebAuthnRegistrationService注册服务
- 实现WebAuthnAuthenticationService认证服务
- 添加WebauthnCredential实体和数据访问层
- 配置WebAuthn服务端参数(RP ID, Origin)
- 实现多设备凭据管理接口
- 添加完整的单元测试和集成测试

安全措施:
- 凭据公钥使用PostgreSQL加密存储
- Challenge随机生成,5分钟过期
- 用户验证要求(User Verification Required)
- 支持FIDO2认证器

测试覆盖率: 88%
性能指标: 注册/认证平均耗时 < 150ms

Closes #123
```

**关键要求:**
- 范围使用服务名 (auth, system, tenant 等)
- 必须包含安全措施说明(如涉及认证授权)
- 必须说明性能影响

### 4. 推送代码并创建 PR

```bash
# 推送分支
git push -u origin feat/auth-webauthn-#123

# 创建 Pull Request
gh pr create \
  --title "feat(auth): 添加WebAuthn无密码认证支持" \
  --body "$(cat <<EOF
## 变更说明
实现 WebAuthn 无密码认证功能,支持生物识别和硬件密钥。

## 主要变更
- 新增 WebAuthn 注册和认证流程
- 新增凭据管理接口
- 数据库新增 webauthn_credential 表

## 测试说明
- 单元测试覆盖率: 88%
- 集成测试: 已验证完整注册认证流程
- 安全测试: 已通过依赖漏洞扫描

## 安全影响
- 影响范围: 认证流程 (新增认证方式,不影响现有流程)
- 安全措施: 凭据加密存储,Challenge防重放
- 需要安全团队审查

## 数据库变更
- 新增表: webauthn_credential (见 migration script)
- 数据迁移: 无

## 配置变更
- 新增配置项: webauthn.rp-id, webauthn.rp-name (见 application.yml)

## 向后兼容性
✅ 完全向后兼容,不影响现有认证流程

Closes #123
EOF
)" \
  --label "type/feature,priority/P1,domain/auth,security-review" \
  --assignee @me
```

**PR 额外要求:**
- 必须说明**安全影响**
- 必须说明**数据库变更**和迁移策略
- 必须说明**配置变更**
- 必须说明**向后兼容性**
- 涉及认证授权的 PR 必须指定安全审查者

### 5. 代码审查

**审查者特殊关注点 (基础服务):**
- [ ] 安全性: 认证绕过、权限提升、数据泄露风险
- [ ] 租户隔离: @DS 注解正确,无跨租户数据泄露
- [ ] 性能影响: 基础服务性能下降会影响全局
- [ ] API 兼容性: 不破坏现有服务的调用
- [ ] 审计日志: 关键操作必须记录审计日志
- [ ] 配置管理: 敏感配置不硬编码,使用环境变量

**强制要求:**
- 至少 **2 名审查者**,其中至少 **1 名安全审查者**
- 安全相关变更必须 **安全团队批准**

### 6. 合并代码

**合并前检查清单:**
- [ ] 所有 CI/CD 检查通过 (构建、测试、安全扫描)
- [ ] 至少 2 名审查者批准 (包括 1 名安全审查者)
- [ ] 代码冲突已解决
- [ ] 数据库迁移脚本已准备(如需要)
- [ ] 配置文档已更新(如需要)
- [ ] 分支基于最新的 master 分支

**合并策略:**
- 使用 `Squash and Merge` 保持主干分支历史清晰

## 分支命名规范

**格式:** `<type>/<service>-<short-desc>-#<issue>`

**服务名缩写:**
- `auth` - scm-auth
- `system` - scm-system
- `tenant` - scm-tenant
- `approval` - scm-approval
- `audit` - scm-audit
- `notify` - scm-notify

**示例:**
- `feat/auth-webauthn-#123`
- `fix/system-permission-cache-#456`
- `refactor/tenant-datasource-#789`

## 提交信息规范

**格式:**
```
<类型>(<服务>): <主题>

[正文]

[安全说明] (如涉及)
[性能影响] (必需)
[数据库变更] (如涉及)

[页脚]
```

**类型 (Type):**
- `feat` - 新功能
- `fix` - Bug修复
- `refactor` - 重构
- `perf` - 性能优化
- `security` - 安全修复 (优先级最高)
- `test` - 测试相关
- `docs` - 文档更新
- `chore` - 构建/配置变更

## GitHub Labels 规范

**必需标签:**
- **优先级:** `priority/P0` ~ `priority/P3`
- **类型:** `type/feature`, `type/bug`, `type/security` 等
- **领域:** `domain/auth`, `domain/system`, `domain/tenant` 等

**特殊标签:**
- `security-review` - 需要安全团队审查 (认证、授权、审计相关)
- `breaking-change` - 破坏性变更 (需特别注意)
- `database-migration` - 涉及数据库结构变更
- `config-change` - 涉及配置项变更

## 最佳实践

### 基础服务特殊注意事项

1. **性能优先**: 基础服务的性能下降会影响所有业务服务
   - 认证接口响应时间 < 100ms
   - 权限查询响应时间 < 50ms
   - 使用缓存减少数据库查询

2. **高可用设计**: 基础服务故障会导致平台不可用
   - 数据库读写分离 + 多从库
   - Redis 哨兵/集群模式
   - 优雅降级策略

3. **安全优先**: 基础服务是安全的第一道防线
   - 所有输入必须验证和过滤
   - 敏感数据加密存储
   - 关键操作记录审计日志

4. **向后兼容**: 基础服务 API 变更影响面广
   - 新增字段使用可选类型
   - 废弃 API 保留至少 2 个版本
   - 使用版本号管理 API

### 数据库多租户最佳实践

```java
// 正确示例: 使用 @DS 注解
@Service
public class SysUserService {

    @DS("user")  // 路由到 db_user 数据库
    public SysUser getUserById(Long userId) {
        return userMapper.selectById(userId);
    }

    @DS("org")  // 路由到 db_org 数据库
    public SysDept getDeptById(Long deptId) {
        return deptMapper.selectById(deptId);
    }
}
```

### 安全编码示例

```java
// 正确示例: 密码验证
@Service
public class SysAuthService {

    // ✅ 使用 Argon2 哈希,不使用明文
    public boolean verifyPassword(String rawPassword, String hashedPassword) {
        return PasswordUtils.verify(rawPassword, hashedPassword);
    }

    // ✅ 敏感信息脱敏
    public SysUserDTO getUserInfo(Long userId) {
        SysUser user = userMapper.selectById(userId);
        return DesensitizeUtils.desensitizeUser(user); // 手机号、身份证等脱敏
    }

    // ✅ 记录审计日志
    @AuditLog(operation = "修改用户权限", resourceType = "USER")
    public void updateUserRoles(Long userId, List<Long> roleIds) {
        // ...
    }
}
```

## 常见问题

**Q: 基础服务的数据库迁移如何处理?**

A: 基础服务使用多数据库架构,必须针对每个数据库准备迁移脚本:
```bash
scripts/db/microservices/
├── 001_db_user.sql        # 用户数据库
├── 002_db_org.sql         # 组织数据库
├── 003_db_permission.sql  # 权限数据库
├── 004_db_approval.sql    # 审批数据库
└── 005_db_audit.sql       # 审计数据库
```

**Q: 如何测试租户隔离?**

A: 编写集成测试,验证不同租户的数据互不影响:
```java
@Test
public void testTenantIsolation() {
    // 设置租户1上下文
    TenantContextHolder.setTenantId(1L);
    List<SysUser> tenant1Users = userService.list();

    // 设置租户2上下文
    TenantContextHolder.setTenantId(2L);
    List<SysUser> tenant2Users = userService.list();

    // 验证数据不重叠
    assertNoOverlap(tenant1Users, tenant2Users);
}
```

**Q: 基础服务如何进行灰度发布?**

A: 使用 Nacos 权重配置进行灰度:
1. 部署新版本实例,权重设置为 10%
2. 观察监控指标和错误日志
3. 逐步提升权重至 100%
4. 下线旧版本实例

---

**文档版本:** v1.0
**适用服务:** scm-auth, scm-system, scm-tenant, scm-approval, scm-audit, scm-notify
**最后更新:** 2025-12-29
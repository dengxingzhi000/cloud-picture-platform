---
name: db-table-design
description: >
  Guide the design of database tables for any business domain. Use this skill whenever
  the user mentions designing, creating, or reviewing database tables, schemas, or data models
  — including requests like "help me design tables for X", "what tables do I need for Y",
  "review my schema", "design the data model for Z", or any mention of ERD, ER diagram,
  database structure, entity design, field design, index design, or table relationships.
  Also trigger when the user describes a business feature or module and needs underlying
  data storage designed (e.g. "I'm building an order system", "help me model user profiles").
  Always use this skill proactively — if the user is discussing any system that needs
  persistent storage, offer to guide the table design even if they haven't explicitly asked.
---

# Database Table Design Skill

Guide users through rigorous, production-grade relational database table design covering
requirements elicitation, entity modeling, field-level design, index strategy, and DDL output.

---

## Phase 0 — Quick Context Capture

Before doing anything, identify:

1. **Business domain** — What is this system for? (e-commerce, SaaS, HR, medical, etc.)
2. **Scale expectation** — Startup MVP or large-scale system? Rough expected data volume?
3. **DB engine** — MySQL / PostgreSQL / TiDB / Oracle / other? Default to MySQL 8 if unspecified.
4. **ORM / Framework** — Spring Data JPA / MyBatis / raw JDBC / other? Affects naming conventions.
5. **Existing context** — Is this greenfield or does existing schema exist?

If domain/system is already clear from the conversation, skip straight to Phase 1.

---

## Phase 1 — Requirements & Entity Discovery

### 1.1 Elicit Core Entities

Ask the user to describe the main "nouns" in their system. Guide them with:

- **Who** are the actors? (user, admin, merchant, customer...)
- **What** are the core business objects? (order, product, invoice, task...)
- **What events** need to be recorded? (payment, log, audit trail, notification...)
- **What relationships** exist? (user places order, order contains products...)

### 1.2 Identify Relationship Types

For each pair of entities, determine cardinality:

| Pattern | Example | Resolution |
|---|---|---|
| One-to-many | User → Orders | FK on the "many" side |
| Many-to-many | Order ↔ Product | Junction/rel table |
| One-to-one | User ↔ UserProfile | FK + UNIQUE, or same table |
| Self-referential | Category → Category | `parent_id` FK on same table |
| Polymorphic | Comment → (Post\|Video) | `target_type` + `target_id` pattern |

### 1.3 Identify Special Requirements

Check for:
- **Soft delete** — logical delete vs physical delete?
- **Multi-tenancy** — `tenant_id` / `org_id` on all tables?
- **Audit trail** — who created/modified? separate audit log table?
- **Versioning** — do any entities need historical snapshots?
- **Internationalisation** — multilingual content fields?
- **Status machines** — what are the state transitions?

---

## Phase 2 — Table Design Principles

Apply these rules to every table.

### 2.1 Primary Keys

**Default: `bigint` auto-increment (MySQL) or `bigserial` (PostgreSQL).**

| Scenario | Recommendation |
|---|---|
| Internal table, no external exposure | `bigint AUTO_INCREMENT` |
| Distributed / sharding needed | Snowflake ID (`bigint`) or `uuid` with application generation |
| API-exposed IDs (security concern) | `uuid` (UUID v4 / v7) |
| Junction table | Composite PK of the two FK columns, no surrogate key |

Never expose auto-increment IDs in public APIs — use UUID or hash ID for external references.

### 2.2 Standard Columns (on every business table)

```sql
id          bigint       NOT NULL AUTO_INCREMENT  COMMENT '主键',
created_at  datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
updated_at  datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                         ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
is_deleted  tinyint(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0正常 1已删除',
```

Add when needed:
```sql
created_by  bigint       COMMENT '创建人ID',
updated_by  bigint       COMMENT '最后修改人ID',
version     int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
tenant_id   bigint       COMMENT '租户ID (多租户场景)',
```

### 2.3 Field Type Selection

| Data Type | Recommended SQL Type | Notes |
|---|---|---|
| Short string (≤64 chars) | `varchar(64)` | Name, code, title |
| Long string (≤255 chars) | `varchar(255)` | URL, description |
| Rich text / JSON blob | `text` or `json` | Use `json` type for queryable JSON |
| Boolean flag | `tinyint(1)` | 0/1; avoid `bool` for MySQL compatibility |
| Status / enum | `tinyint` | Map to Java enum; add COMMENT with values |
| Money / price | `decimal(12,2)` | NEVER use `float`/`double` for currency |
| Percentage / rate | `decimal(6,4)` | e.g. 0.0750 = 7.50% |
| Unix timestamp | `bigint` | Epoch ms; or `datetime(3)` for readability |
| Date only | `date` | No time component |
| IP Address | `varchar(45)` | Supports IPv6 |
| Phone number | `varchar(20)` | Include country code |
| ID card / passport | `varchar(32)` | Encrypted at rest recommended |

### 2.4 Naming Conventions

- **Tables**: `snake_case`, singular noun preferred, business-prefixed for modules (`order_item`, `pay_record`)
- **Columns**: `snake_case`, avoid reserved words (`type` → `biz_type`, `status` → `order_status`)
- **FK columns**: `{entity}_id` (e.g. `user_id`, `order_id`)
- **Boolean**: `is_` prefix (`is_deleted`, `is_enabled`, `is_default`)
- **Time**: `_at` suffix for datetime (`created_at`, `paid_at`, `expired_at`)
- **Enum/Status**: value range documented in COMMENT: `COMMENT '状态: 1待支付 2已支付 3已取消'`

### 2.5 Constraints

```sql
-- NOT NULL as default for all business columns; NULL only when semantically absent
-- String columns: NOT NULL DEFAULT '' rather than nullable
-- Numeric: NOT NULL DEFAULT 0 unless null has distinct business meaning

-- Use UNIQUE constraints for natural business keys:
UNIQUE KEY uk_user_email (email),
UNIQUE KEY uk_order_no (order_no),

-- Use CHECK constraints (MySQL 8+) for enum validation:
CONSTRAINT chk_status CHECK (status IN (1,2,3,4))
```

---

## Phase 3 — Index Design

Read `references/index-strategy.md` for detailed index patterns.

### 3.1 Index Quick Rules

1. **All FK columns** get an index (prevents full table scan on JOIN)
2. **All query filter columns** used in WHERE clauses get indexed
3. **Status + time** columns used for paginated list queries: composite `(status, created_at)`
4. **Prefix with high-cardinality columns** in composite indexes
5. **Cover the SELECT columns** in covering indexes for hot query paths
6. **Avoid over-indexing** write-heavy tables — each index slows writes

### 3.2 Index Naming

```sql
PRIMARY KEY (id),
UNIQUE KEY  uk_{table}_{column}  (column),
KEY         idx_{table}_{columns} (col1, col2),
```

---

## Phase 4 — DDL Output Format

Generate complete, production-ready DDL. Use this template:

```sql
CREATE TABLE `{table_name}` (
  `id`          bigint       NOT NULL AUTO_INCREMENT        COMMENT '主键ID',
  -- business columns --
  `created_at`  datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`  datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                             ON UPDATE CURRENT_TIMESTAMP(3)         COMMENT '更新时间',
  `is_deleted`  tinyint(1)   NOT NULL DEFAULT 0                     COMMENT '逻辑删除: 0正常 1已删除',
  PRIMARY KEY (`id`),
  -- unique keys --
  -- indexes --
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='{table comment}';
```

Always use:
- `ENGINE=InnoDB` — transactional support
- `CHARSET=utf8mb4` — full Unicode including emoji
- `COLLATE=utf8mb4_unicode_ci` — case-insensitive, accent-sensitive

---

## Phase 5 — Review Checklist

Before finalizing, run through:

- [ ] Every table has `id`, `created_at`, `updated_at`, `is_deleted`
- [ ] No `float`/`double` for money fields
- [ ] All status/type columns have COMMENT documenting enum values
- [ ] All FK columns are indexed
- [ ] Sensitive fields (phone, ID card, password) noted for encryption
- [ ] Many-to-many relationships resolved via junction table
- [ ] Self-referential trees have `parent_id` with proper index
- [ ] Multi-tenant tables include `tenant_id` with index
- [ ] Public-facing IDs use UUID strategy (not sequential bigint)
- [ ] Optimistic lock `version` column added for high-contention entities

---

## Phase 6 — Extended Artifacts (optional, offer to generate)

After DDL, offer to also produce:

1. **ERD diagram** — Mermaid `erDiagram` rendered visually
2. **Java entity classes** — JPA `@Entity` or MyBatis POJO with Lombok
3. **MyBatis XML Mapper** — CRUD + list query with dynamic WHERE
4. **Liquibase / Flyway migration** — versioned changelog file
5. **Data dictionary** — Markdown table documenting all fields

---

## Reference Files

- `references/index-strategy.md` — Detailed index design patterns and anti-patterns
- `references/domain-templates.md` — Pre-built table templates by business domain (user, order, payment, tenant, etc.)

Load the relevant reference when the user's domain matches a template, or when designing indexes for a complex query pattern.
# Index Strategy Reference

## Core Index Patterns

### 1. Single Column Index
Use when: simple equality or range filter on one column.
```sql
KEY idx_order_user_id (user_id)
KEY idx_order_status (status)
```

### 2. Composite Index (Most Important Pattern)
**Column order rule: equality first, range last, highest cardinality first within equality group.**

```sql
-- Query: WHERE tenant_id = ? AND status = ? AND created_at > ?
KEY idx_order_tenant_status_time (tenant_id, status, created_at)
-- ✅ tenant_id (equality, high cardinality) → status (equality) → created_at (range, always last)

-- ❌ Wrong order:
KEY idx_order_time_status (created_at, status)
-- Range on created_at kills the status filter — MySQL stops using index after first range column
```

### 3. Covering Index
Include SELECT columns to avoid table row lookups entirely.
```sql
-- Query: SELECT id, status, created_at FROM orders WHERE user_id = ? ORDER BY created_at DESC
KEY idx_order_user_cover (user_id, created_at, status, id)
-- All needed columns are in the index — no row fetch needed
```

### 4. Prefix Index (for long VARCHAR)
When full-column index is too large:
```sql
KEY idx_user_email_prefix (email(20))
-- Only use when LIKE 'prefix%' pattern — can't use for equality on full value
```

### 5. Unique Index as Business Key
```sql
UNIQUE KEY uk_user_email (email)
UNIQUE KEY uk_order_no (order_no)
UNIQUE KEY uk_tenant_code (tenant_id, code)  -- scoped unique within tenant
```

---

## Index Anti-Patterns

| Anti-Pattern | Problem | Fix |
|---|---|---|
| Index on `is_deleted` alone | Very low cardinality (0/1) — useless | Composite: `(is_deleted, status, created_at)` |
| Too many indexes on write-heavy table | Each INSERT/UPDATE maintains all indexes | Keep ≤5 indexes on tables with >1000 writes/sec |
| Leading range column in composite | MySQL stops using remaining columns after range | Put range column last |
| Index on computed expression | Won't be used unless functional index (MySQL 8+) | Use functional index or precompute column |
| Duplicate indexes | `(a)` and `(a, b)` — first is redundant | Remove `(a)` since `(a, b)` covers it |
| Nullable column in unique index | Multiple NULLs allowed in unique index — subtle bug | Use `NOT NULL DEFAULT ''` for unique columns |

---

## Pagination Index Pattern

For cursor-based pagination (high performance, large datasets):
```sql
-- Query: WHERE user_id = ? AND id > {last_id} ORDER BY id ASC LIMIT 20
KEY idx_order_user_id (user_id, id)
-- id is PK — already indexed; composite covers user_id filter + id ordering
```

For offset pagination (simple but degrades at large offsets):
```sql
-- Query: WHERE status = 1 ORDER BY created_at DESC LIMIT 20 OFFSET 10000
KEY idx_order_status_time (status, created_at)
-- Still slow at large offset — warn users and suggest cursor-based
```

---

## Soft Delete Index Pattern

When `is_deleted = 0` is in every query, include it in composite:
```sql
-- Instead of:
KEY idx_order_user (user_id)

-- Use:
KEY idx_order_user_active (user_id, is_deleted)
-- Or with MySQL 8+ partial/functional index:
-- The optimizer may skip is_deleted=0 filter from index if cardinality is too low
-- Test with EXPLAIN in your specific dataset
```

---

## Multi-Tenant Index Pattern

Every query in a multi-tenant system includes `tenant_id`. It should always be the **first column** in every composite index:
```sql
KEY idx_product_tenant_status (tenant_id, status, created_at)
KEY idx_product_tenant_category (tenant_id, category_id)
UNIQUE KEY uk_product_tenant_code (tenant_id, product_code)
```

---

## Index for Full-Text Search

For Chinese text search, MySQL full-text index won't work well. Options:
1. **Elasticsearch / OpenSearch** — sync via Canal or MQ, query ES for IDs, fetch from DB
2. **MySQL LIKE prefix** — `LIKE 'keyword%'` can use index prefix; `LIKE '%keyword%'` cannot
3. **Separate keyword column** — store searchable tokens in a `keywords varchar(500)` column with FULLTEXT index

```sql
-- Option 3: add FULLTEXT index
FULLTEXT KEY ft_product_keywords (keywords)
-- Query: WHERE MATCH(keywords) AGAINST ('关键词' IN BOOLEAN MODE)
```

---

## EXPLAIN Interpretation Quick Reference

```sql
EXPLAIN SELECT ...;
```

| Column | Watch For |
|---|---|
| `type` | `ALL` = full scan (bad), `ref`/`range`/`eq_ref` = index used (good) |
| `key` | NULL = no index used |
| `rows` | High rows × filtered = bad; low rows = good |
| `Extra` | `Using filesort` = sort not using index; `Using temporary` = temp table; `Using index` = covering index (great) |

Target: `type` in (`eq_ref`, `ref`, `range`) and `Extra` showing `Using index` for hot paths.
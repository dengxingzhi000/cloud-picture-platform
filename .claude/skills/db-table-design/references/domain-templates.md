# Domain Table Templates

Pre-built table designs for common business domains. Use as starting points — adapt to the user's specific requirements.

---

## Table of Contents
1. [User & Identity](#1-user--identity)
2. [Order & E-Commerce](#2-order--e-commerce)
3. [Payment](#3-payment)
4. [Product & Catalog](#4-product--catalog)
5. [Tenant & Organization](#5-tenant--organization)
6. [Notification & Message](#6-notification--message)
7. [File & Resource](#7-file--resource)
8. [Audit Log](#8-audit-log)

---

## 1. User & Identity

```sql
CREATE TABLE `user` (
  `id`            bigint       NOT NULL AUTO_INCREMENT     COMMENT '用户ID',
  `uuid`          varchar(36)  NOT NULL                    COMMENT '对外暴露的UUID',
  `username`      varchar(64)  NOT NULL DEFAULT ''         COMMENT '用户名',
  `email`         varchar(128) NOT NULL DEFAULT ''         COMMENT '邮箱',
  `phone`         varchar(20)  NOT NULL DEFAULT ''         COMMENT '手机号(加密存储)',
  `password_hash` varchar(128) NOT NULL DEFAULT ''         COMMENT '密码哈希(bcrypt)',
  `avatar_url`    varchar(512) NOT NULL DEFAULT ''         COMMENT '头像URL',
  `nickname`      varchar(64)  NOT NULL DEFAULT ''         COMMENT '昵称',
  `gender`        tinyint      NOT NULL DEFAULT 0          COMMENT '性别: 0未知 1男 2女',
  `status`        tinyint      NOT NULL DEFAULT 1          COMMENT '状态: 1正常 2禁用 3注销',
  `last_login_at` datetime(3)                              COMMENT '最后登录时间',
  `created_at`    datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`    datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `is_deleted`    tinyint(1)   NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_uuid` (`uuid`),
  UNIQUE KEY `uk_user_email` (`email`),
  UNIQUE KEY `uk_user_phone` (`phone`),
  KEY `idx_user_status_time` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户主表';

CREATE TABLE `user_oauth` (
  `id`            bigint       NOT NULL AUTO_INCREMENT,
  `user_id`       bigint       NOT NULL                    COMMENT '用户ID',
  `platform`      varchar(32)  NOT NULL                    COMMENT '平台: wechat/github/google',
  `open_id`       varchar(128) NOT NULL                    COMMENT '三方平台OpenID',
  `union_id`      varchar(128) NOT NULL DEFAULT ''         COMMENT '三方平台UnionID',
  `access_token`  varchar(512) NOT NULL DEFAULT ''         COMMENT 'AccessToken(加密)',
  `created_at`    datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`    datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_oauth_platform_open` (`platform`, `open_id`),
  KEY `idx_oauth_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='第三方登录绑定';
```

---

## 2. Order & E-Commerce

```sql
CREATE TABLE `order` (
  `id`            bigint         NOT NULL AUTO_INCREMENT,
  `order_no`      varchar(32)    NOT NULL                  COMMENT '订单号(业务唯一)',
  `user_id`       bigint         NOT NULL                  COMMENT '用户ID',
  `status`        tinyint        NOT NULL DEFAULT 1        COMMENT '状态: 1待支付 2已支付 3已发货 4已完成 5已取消',
  `total_amount`  decimal(12,2)  NOT NULL DEFAULT 0.00     COMMENT '订单总金额',
  `pay_amount`    decimal(12,2)  NOT NULL DEFAULT 0.00     COMMENT '实付金额',
  `discount_amt`  decimal(12,2)  NOT NULL DEFAULT 0.00     COMMENT '优惠金额',
  `pay_method`    tinyint        NOT NULL DEFAULT 0        COMMENT '支付方式: 1微信 2支付宝 3余额',
  `pay_at`        datetime(3)                              COMMENT '支付时间',
  `remark`        varchar(255)   NOT NULL DEFAULT ''       COMMENT '买家备注',
  `address_snap`  json                                     COMMENT '收货地址快照',
  `created_at`    datetime(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`    datetime(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `is_deleted`    tinyint(1)     NOT NULL DEFAULT 0,
  `version`       int            NOT NULL DEFAULT 0        COMMENT '乐观锁',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_order_user_status` (`user_id`, `status`, `created_at`),
  KEY `idx_order_status_time` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单主表';

CREATE TABLE `order_item` (
  `id`            bigint         NOT NULL AUTO_INCREMENT,
  `order_id`      bigint         NOT NULL                  COMMENT '订单ID',
  `order_no`      varchar(32)    NOT NULL                  COMMENT '订单号(冗余)',
  `product_id`    bigint         NOT NULL                  COMMENT '商品ID',
  `sku_id`        bigint         NOT NULL                  COMMENT 'SKU ID',
  `product_snap`  json           NOT NULL                  COMMENT '商品快照(名称/图片/规格)',
  `quantity`      int            NOT NULL DEFAULT 1        COMMENT '购买数量',
  `unit_price`    decimal(12,2)  NOT NULL                  COMMENT '单价(下单时)',
  `total_price`   decimal(12,2)  NOT NULL                  COMMENT '小计金额',
  `created_at`    datetime(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_item_order` (`order_id`),
  KEY `idx_item_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单明细';
```

---

## 3. Payment

```sql
CREATE TABLE `pay_record` (
  `id`             bigint         NOT NULL AUTO_INCREMENT,
  `pay_no`         varchar(64)    NOT NULL                 COMMENT '支付流水号',
  `order_no`       varchar(32)    NOT NULL                 COMMENT '关联订单号',
  `user_id`        bigint         NOT NULL,
  `channel`        tinyint        NOT NULL                 COMMENT '支付渠道: 1微信 2支付宝',
  `channel_trx_no` varchar(64)    NOT NULL DEFAULT ''      COMMENT '三方平台交易号',
  `amount`         decimal(12,2)  NOT NULL                 COMMENT '支付金额',
  `currency`       varchar(8)     NOT NULL DEFAULT 'CNY'   COMMENT '币种',
  `status`         tinyint        NOT NULL DEFAULT 1       COMMENT '状态: 1待支付 2支付成功 3支付失败 4已退款',
  `paid_at`        datetime(3)                             COMMENT '支付成功时间',
  `raw_notify`     text                                    COMMENT '三方回调原始报文',
  `created_at`     datetime(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`     datetime(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pay_no` (`pay_no`),
  KEY `idx_pay_order` (`order_no`),
  KEY `idx_pay_user_status` (`user_id`, `status`),
  KEY `idx_pay_channel_trx` (`channel_trx_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付记录';

CREATE TABLE `refund_record` (
  `id`             bigint         NOT NULL AUTO_INCREMENT,
  `refund_no`      varchar(64)    NOT NULL,
  `pay_no`         varchar(64)    NOT NULL,
  `order_no`       varchar(32)    NOT NULL,
  `amount`         decimal(12,2)  NOT NULL,
  `reason`         varchar(255)   NOT NULL DEFAULT '',
  `status`         tinyint        NOT NULL DEFAULT 1       COMMENT '状态: 1处理中 2退款成功 3退款失败',
  `channel_ref_no` varchar(64)    NOT NULL DEFAULT '',
  `refunded_at`    datetime(3),
  `created_at`     datetime(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`     datetime(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refund_no` (`refund_no`),
  KEY `idx_refund_pay` (`pay_no`),
  KEY `idx_refund_order` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='退款记录';
```

---

## 4. Product & Catalog

```sql
CREATE TABLE `product` (
  `id`            bigint         NOT NULL AUTO_INCREMENT,
  `category_id`   bigint         NOT NULL,
  `name`          varchar(128)   NOT NULL,
  `code`          varchar(64)    NOT NULL DEFAULT '',
  `cover_url`     varchar(512)   NOT NULL DEFAULT '',
  `description`   text,
  `price`         decimal(12,2)  NOT NULL DEFAULT 0.00     COMMENT '展示价',
  `cost_price`    decimal(12,2)  NOT NULL DEFAULT 0.00     COMMENT '成本价',
  `stock`         int            NOT NULL DEFAULT 0        COMMENT '库存(无SKU时用)',
  `sold_count`    int            NOT NULL DEFAULT 0        COMMENT '累计销量',
  `status`        tinyint        NOT NULL DEFAULT 0        COMMENT '状态: 0草稿 1上架 2下架',
  `sort`          int            NOT NULL DEFAULT 0,
  `created_at`    datetime(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`    datetime(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `is_deleted`    tinyint(1)     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_product_category_status` (`category_id`, `status`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品主表';

CREATE TABLE `product_sku` (
  `id`            bigint         NOT NULL AUTO_INCREMENT,
  `product_id`    bigint         NOT NULL,
  `sku_code`      varchar(64)    NOT NULL,
  `spec_values`   json           NOT NULL                  COMMENT '规格值快照 [{specId,specName,valueId,valueName}]',
  `price`         decimal(12,2)  NOT NULL,
  `stock`         int            NOT NULL DEFAULT 0,
  `is_deleted`    tinyint(1)     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sku_code` (`sku_code`),
  KEY `idx_sku_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品SKU';
```

---

## 5. Tenant & Organization

```sql
CREATE TABLE `tenant` (
  `id`            bigint       NOT NULL AUTO_INCREMENT,
  `code`          varchar(32)  NOT NULL                    COMMENT '租户标识(唯一)',
  `name`          varchar(128) NOT NULL,
  `plan`          tinyint      NOT NULL DEFAULT 1          COMMENT '套餐: 1免费 2标准 3企业',
  `status`        tinyint      NOT NULL DEFAULT 1          COMMENT '状态: 1正常 2冻结 3注销',
  `expire_at`     datetime(3)                              COMMENT '到期时间(null=永久)',
  `contact_email` varchar(128) NOT NULL DEFAULT '',
  `contact_phone` varchar(20)  NOT NULL DEFAULT '',
  `created_at`    datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`    datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `is_deleted`    tinyint(1)   NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户';

CREATE TABLE `role` (
  `id`            bigint       NOT NULL AUTO_INCREMENT,
  `tenant_id`     bigint       NOT NULL DEFAULT 0          COMMENT '0=系统内置角色',
  `code`          varchar(64)  NOT NULL,
  `name`          varchar(64)  NOT NULL,
  `type`          tinyint      NOT NULL DEFAULT 2          COMMENT '类型: 1系统 2自定义',
  `is_deleted`    tinyint(1)   NOT NULL DEFAULT 0,
  `created_at`    datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_tenant_code` (`tenant_id`, `code`),
  KEY `idx_role_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色';

CREATE TABLE `user_role_rel` (
  `id`        bigint   NOT NULL AUTO_INCREMENT,
  `user_id`   bigint   NOT NULL,
  `role_id`   bigint   NOT NULL,
  `tenant_id` bigint   NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`, `tenant_id`),
  KEY `idx_urr_tenant_role` (`tenant_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联';
```

---

## 6. Notification & Message

```sql
CREATE TABLE `notification` (
  `id`          bigint       NOT NULL AUTO_INCREMENT,
  `user_id`     bigint       NOT NULL,
  `type`        tinyint      NOT NULL                      COMMENT '类型: 1系统 2订单 3活动 4评论',
  `title`       varchar(128) NOT NULL DEFAULT '',
  `content`     text         NOT NULL,
  `biz_type`    varchar(32)  NOT NULL DEFAULT ''           COMMENT '业务类型(跳转用)',
  `biz_id`      bigint       NOT NULL DEFAULT 0            COMMENT '业务ID',
  `is_read`     tinyint(1)   NOT NULL DEFAULT 0,
  `read_at`     datetime(3),
  `created_at`  datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_notify_user_read` (`user_id`, `is_read`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站内通知';
```

---

## 7. File & Resource

```sql
CREATE TABLE `file_resource` (
  `id`           bigint        NOT NULL AUTO_INCREMENT,
  `uploader_id`  bigint        NOT NULL,
  `bucket`       varchar(64)   NOT NULL                    COMMENT '存储桶名',
  `object_key`   varchar(512)  NOT NULL                    COMMENT '对象存储Key',
  `url`          varchar(1024) NOT NULL                    COMMENT '访问URL',
  `filename`     varchar(255)  NOT NULL                    COMMENT '原始文件名',
  `mime_type`    varchar(64)   NOT NULL DEFAULT '',
  `size_bytes`   bigint        NOT NULL DEFAULT 0          COMMENT '文件大小(字节)',
  `biz_type`     varchar(32)   NOT NULL DEFAULT ''         COMMENT '业务归属类型',
  `biz_id`       bigint        NOT NULL DEFAULT 0,
  `created_at`   datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `is_deleted`   tinyint(1)    NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_file_uploader` (`uploader_id`),
  KEY `idx_file_biz` (`biz_type`, `biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件资源';
```

---

## 8. Audit Log

```sql
CREATE TABLE `audit_log` (
  `id`           bigint        NOT NULL AUTO_INCREMENT,
  `tenant_id`    bigint        NOT NULL DEFAULT 0,
  `operator_id`  bigint        NOT NULL,
  `operator_name` varchar(64)  NOT NULL DEFAULT '',
  `action`       varchar(64)   NOT NULL                    COMMENT '操作动作: CREATE/UPDATE/DELETE',
  `biz_type`     varchar(64)   NOT NULL                    COMMENT '业务类型',
  `biz_id`       varchar(64)   NOT NULL                    COMMENT '业务ID',
  `before_data`  json                                      COMMENT '变更前数据',
  `after_data`   json                                      COMMENT '变更后数据',
  `ip`           varchar(45)   NOT NULL DEFAULT '',
  `user_agent`   varchar(255)  NOT NULL DEFAULT '',
  `request_id`   varchar(64)   NOT NULL DEFAULT '',
  `created_at`   datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_audit_tenant_biz` (`tenant_id`, `biz_type`, `biz_id`),
  KEY `idx_audit_operator` (`operator_id`, `created_at`),
  KEY `idx_audit_time` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作审计日志'
  PARTITION BY RANGE (YEAR(`created_at`) * 100 + MONTH(`created_at`)) (
    PARTITION p202501 VALUES LESS THAN (202502),
    PARTITION p202502 VALUES LESS THAN (202503),
    -- ... add monthly partitions as needed
    PARTITION p_future VALUES LESS THAN MAXVALUE
  );
-- Note: audit_log often benefits from monthly range partitioning for purging old data
```
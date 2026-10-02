-- ============================================================
-- 多端智能外卖点餐平台 建库脚本
-- MySQL 8.0 / utf8mb4
-- 管理员与演示账号由后端 DataInitializer 首次启动时自动创建
-- ============================================================
CREATE DATABASE IF NOT EXISTS waimai DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE waimai;

CREATE TABLE `user` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
  `phone`         VARCHAR(20)  NOT NULL,
  `password_hash` VARCHAR(100) NOT NULL,
  `nickname`      VARCHAR(50)  NOT NULL,
  `avatar`        VARCHAR(255) DEFAULT NULL,
  `role`          VARCHAR(20)  NOT NULL COMMENT 'USER/MERCHANT/RIDER/ADMIN',
  `status`        TINYINT      NOT NULL DEFAULT 1,
  `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_phone` (`phone`),
  KEY `idx_role_status` (`role`,`status`)
) ENGINE=InnoDB COMMENT='统一账号表';

CREATE TABLE `address` (
  `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id`     BIGINT NOT NULL,
  `contact`     VARCHAR(50) NOT NULL,
  `phone`       VARCHAR(20) NOT NULL,
  `gender`      TINYINT NOT NULL DEFAULT 1,
  `province`    VARCHAR(50) NOT NULL,
  `city`        VARCHAR(50) NOT NULL,
  `district`    VARCHAR(50) NOT NULL,
  `detail`      VARCHAR(200) NOT NULL,
  `lng`         DECIMAL(10,6) NOT NULL,
  `lat`         DECIMAL(10,6) NOT NULL,
  `tag`         VARCHAR(20) DEFAULT NULL,
  `is_default`  TINYINT NOT NULL DEFAULT 0,
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB COMMENT='收货地址';

CREATE TABLE `favorite` (
  `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id`     BIGINT NOT NULL,
  `merchant_id` BIGINT NOT NULL,
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_user_merchant` (`user_id`,`merchant_id`)
) ENGINE=InnoDB COMMENT='收藏商家';

CREATE TABLE `search_history` (
  `id`       BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id`  BIGINT NOT NULL,
  `keyword`  VARCHAR(100) NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_user` (`user_id`,`created_at`)
) ENGINE=InnoDB COMMENT='搜索历史';

CREATE TABLE `merchant_category` (
  `id`    INT AUTO_INCREMENT PRIMARY KEY,
  `name`  VARCHAR(50) NOT NULL,
  `icon`  VARCHAR(255) DEFAULT NULL,
  `sort`  INT NOT NULL DEFAULT 0
) ENGINE=InnoDB COMMENT='商家分类';

CREATE TABLE `merchant` (
  `id`               BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id`          BIGINT NOT NULL,
  `shop_name`        VARCHAR(100) NOT NULL,
  `category_id`      INT NOT NULL,
  `logo`             VARCHAR(255) DEFAULT NULL,
  `cover`            VARCHAR(255) DEFAULT NULL,
  `notice`           VARCHAR(500) DEFAULT NULL,
  `phone`            VARCHAR(20) NOT NULL,
  `address`          VARCHAR(200) NOT NULL,
  `lng`              DECIMAL(10,6) NOT NULL,
  `lat`              DECIMAL(10,6) NOT NULL,
  `business_hours`   VARCHAR(50) DEFAULT '09:00-21:00',
  `min_order_amount` DECIMAL(10,2) NOT NULL DEFAULT 20.00,
  `delivery_fee`     DECIMAL(10,2) NOT NULL DEFAULT 3.00,
  `rating`           DECIMAL(3,1) NOT NULL DEFAULT 4.8,
  `monthly_sales`    INT NOT NULL DEFAULT 0,
  `open_status`      TINYINT NOT NULL DEFAULT 1,
  `audit_status`     TINYINT NOT NULL DEFAULT 0,
  `audit_remark`     VARCHAR(200) DEFAULT NULL,
  `created_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_user` (`user_id`),
  KEY `idx_category_audit` (`category_id`,`audit_status`),
  KEY `idx_lnglat` (`lng`,`lat`)
) ENGINE=InnoDB COMMENT='商家';

CREATE TABLE `dish_category` (
  `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
  `merchant_id` BIGINT NOT NULL,
  `name`        VARCHAR(50) NOT NULL,
  `sort`        INT NOT NULL DEFAULT 0,
  KEY `idx_merchant` (`merchant_id`)
) ENGINE=InnoDB COMMENT='菜品分类';

CREATE TABLE `dish` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
  `merchant_id`   BIGINT NOT NULL,
  `category_id`   BIGINT NOT NULL,
  `name`          VARCHAR(100) NOT NULL,
  `description`   VARCHAR(500) DEFAULT NULL,
  `image`         VARCHAR(255) DEFAULT NULL,
  `price`         DECIMAL(10,2) NOT NULL,
  `original_price` DECIMAL(10,2) DEFAULT NULL,
  `unit`          VARCHAR(20) DEFAULT '份',
  `stock`         INT NOT NULL DEFAULT 999,
  `monthly_sales` INT NOT NULL DEFAULT 0,
  `rating`        DECIMAL(3,1) NOT NULL DEFAULT 4.8,
  `tags`          VARCHAR(200) DEFAULT NULL,
  `is_recommend`  TINYINT NOT NULL DEFAULT 0,
  `status`        TINYINT NOT NULL DEFAULT 1,
  `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_merchant_status` (`merchant_id`,`status`),
  KEY `idx_sales` (`monthly_sales`)
) ENGINE=InnoDB COMMENT='菜品';

CREATE TABLE `shopping_cart` (
  `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id`     BIGINT NOT NULL,
  `merchant_id` BIGINT NOT NULL,
  `dish_id`     BIGINT NOT NULL,
  `dish_name`   VARCHAR(100) NOT NULL,
  `image`       VARCHAR(255) DEFAULT NULL,
  `price`       DECIMAL(10,2) NOT NULL,
  `quantity`    INT NOT NULL DEFAULT 1,
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_user_dish` (`user_id`,`dish_id`)
) ENGINE=InnoDB COMMENT='购物车';

CREATE TABLE `coupon` (
  `id`               BIGINT AUTO_INCREMENT PRIMARY KEY,
  `merchant_id`      BIGINT NOT NULL DEFAULT 0,
  `name`             VARCHAR(100) NOT NULL,
  `type`             TINYINT NOT NULL COMMENT '1满减 2折扣 3无门槛',
  `threshold_amount` DECIMAL(10,2) NOT NULL DEFAULT 0,
  `discount_amount`  DECIMAL(10,2) DEFAULT NULL,
  `discount_rate`    DECIMAL(3,2) DEFAULT NULL,
  `total_count`      INT NOT NULL DEFAULT 0,
  `received_count`   INT NOT NULL DEFAULT 0,
  `per_user_limit`   INT NOT NULL DEFAULT 1,
  `start_time`       DATETIME NOT NULL,
  `end_time`         DATETIME NOT NULL,
  `status`           TINYINT NOT NULL DEFAULT 1,
  `created_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_merchant` (`merchant_id`,`status`)
) ENGINE=InnoDB COMMENT='优惠券模板';

CREATE TABLE `user_coupon` (
  `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
  `coupon_id`   BIGINT NOT NULL,
  `user_id`     BIGINT NOT NULL,
  `status`      TINYINT NOT NULL DEFAULT 0 COMMENT '0未使用 1已使用 2已过期',
  `received_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `used_at`     DATETIME DEFAULT NULL,
  `order_id`    BIGINT DEFAULT NULL,
  KEY `idx_user_status` (`user_id`,`status`)
) ENGINE=InnoDB COMMENT='用户持有的优惠券';

CREATE TABLE `orders` (
  `id`               BIGINT AUTO_INCREMENT PRIMARY KEY,
  `order_no`         VARCHAR(32) NOT NULL,
  `user_id`          BIGINT NOT NULL,
  `merchant_id`      BIGINT NOT NULL,
  `rider_id`         BIGINT DEFAULT NULL,
  `address_snapshot` JSON NOT NULL,
  `dish_amount`      DECIMAL(10,2) NOT NULL,
  `delivery_fee`     DECIMAL(10,2) NOT NULL,
  `package_fee`      DECIMAL(10,2) NOT NULL DEFAULT 0,
  `discount_amount`  DECIMAL(10,2) NOT NULL DEFAULT 0,
  `pay_amount`       DECIMAL(10,2) NOT NULL,
  `user_coupon_id`   BIGINT DEFAULT NULL,
  `status`           VARCHAR(30) NOT NULL DEFAULT 'PENDING_PAYMENT',
  `remark`           VARCHAR(200) DEFAULT NULL,
  `pay_time`         DATETIME DEFAULT NULL,
  `accept_time`      DATETIME DEFAULT NULL,
  `pickup_time`      DATETIME DEFAULT NULL,
  `delivered_time`   DATETIME DEFAULT NULL,
  `cancel_reason`    VARCHAR(200) DEFAULT NULL,
  `cancel_by`        VARCHAR(20) DEFAULT NULL,
  `created_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user` (`user_id`,`status`),
  KEY `idx_merchant` (`merchant_id`,`status`),
  KEY `idx_rider` (`rider_id`,`status`)
) ENGINE=InnoDB COMMENT='订单主表';

CREATE TABLE `order_item` (
  `id`        BIGINT AUTO_INCREMENT PRIMARY KEY,
  `order_id`  BIGINT NOT NULL,
  `dish_id`   BIGINT NOT NULL,
  `dish_name` VARCHAR(100) NOT NULL,
  `image`     VARCHAR(255) DEFAULT NULL,
  `price`     DECIMAL(10,2) NOT NULL,
  `quantity`  INT NOT NULL,
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB COMMENT='订单明细';

CREATE TABLE `order_status_log` (
  `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
  `order_id`    BIGINT NOT NULL,
  `from_status` VARCHAR(30) DEFAULT NULL,
  `to_status`   VARCHAR(30) NOT NULL,
  `operator`    VARCHAR(50) DEFAULT NULL,
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB COMMENT='状态流转日志';

CREATE TABLE `rider` (
  `id`           BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id`      BIGINT NOT NULL,
  `real_name`    VARCHAR(50) NOT NULL,
  `phone`        VARCHAR(20) NOT NULL,
  `vehicle`      VARCHAR(20) NOT NULL DEFAULT '电动车',
  `audit_status` TINYINT NOT NULL DEFAULT 0,
  `work_status`  TINYINT NOT NULL DEFAULT 0 COMMENT '0离线 1空闲 2配送中',
  `today_orders` INT NOT NULL DEFAULT 0,
  `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_user` (`user_id`)
) ENGINE=InnoDB COMMENT='骑手';

CREATE TABLE `rider_location` (
  `id`         BIGINT AUTO_INCREMENT PRIMARY KEY,
  `rider_id`   BIGINT NOT NULL,
  `order_id`   BIGINT DEFAULT NULL,
  `lng`        DECIMAL(10,6) NOT NULL,
  `lat`        DECIMAL(10,6) NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_rider_time` (`rider_id`,`created_at`)
) ENGINE=InnoDB COMMENT='骑手轨迹归档';

CREATE TABLE `review` (
  `id`             BIGINT AUTO_INCREMENT PRIMARY KEY,
  `order_id`       BIGINT NOT NULL,
  `user_id`        BIGINT NOT NULL,
  `merchant_id`    BIGINT NOT NULL,
  `rating`         TINYINT NOT NULL,
  `content`        VARCHAR(500) DEFAULT NULL,
  `images`         VARCHAR(1000) DEFAULT NULL,
  `sentiment`      VARCHAR(10) DEFAULT NULL,
  `sentiment_score` DECIMAL(4,3) DEFAULT NULL,
  `reply`          VARCHAR(500) DEFAULT NULL,
  `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_order` (`order_id`),
  KEY `idx_merchant` (`merchant_id`,`created_at`)
) ENGINE=InnoDB COMMENT='订单评价';

CREATE TABLE `bid_campaign` (
  `id`           BIGINT AUTO_INCREMENT PRIMARY KEY,
  `merchant_id`  BIGINT NOT NULL,
  `keyword`      VARCHAR(50) NOT NULL,
  `bid`          DECIMAL(6,2) NOT NULL,
  `daily_budget` DECIMAL(8,2) NOT NULL,
  `today_spent`  DECIMAL(8,2) NOT NULL DEFAULT 0,
  `status`       TINYINT NOT NULL DEFAULT 0 COMMENT '0待审核 1投放中 2暂停 3已结束',
  `start_date`   DATE NOT NULL,
  `end_date`     DATE NOT NULL,
  `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_keyword_status` (`keyword`,`status`),
  KEY `idx_merchant` (`merchant_id`)
) ENGINE=InnoDB COMMENT='竞价投放';

CREATE TABLE `bid_log` (
  `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
  `campaign_id` BIGINT NOT NULL,
  `user_id`     BIGINT DEFAULT NULL,
  `position`    INT NOT NULL,
  `clicked`     TINYINT NOT NULL DEFAULT 0,
  `cost`        DECIMAL(6,2) NOT NULL DEFAULT 0,
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_campaign` (`campaign_id`,`created_at`)
) ENGINE=InnoDB COMMENT='竞价曝光/扣费';

CREATE TABLE `user_behavior` (
  `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id`     BIGINT NOT NULL,
  `merchant_id` BIGINT NOT NULL,
  `dish_id`     BIGINT DEFAULT NULL,
  `action`      VARCHAR(20) NOT NULL COMMENT 'VIEW/CART/ORDER/FAV/REVIEW',
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_user` (`user_id`,`created_at`),
  KEY `idx_dish` (`dish_id`)
) ENGINE=InnoDB COMMENT='用户行为流水';

CREATE TABLE `recommend_log` (
  `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id`     BIGINT NOT NULL,
  `dish_id`     BIGINT NOT NULL,
  `merchant_id` BIGINT NOT NULL,
  `source`      VARCHAR(20) NOT NULL,
  `score`       DECIMAL(8,4) NOT NULL,
  `is_exposed`  TINYINT NOT NULL DEFAULT 1,
  `is_clicked`  TINYINT NOT NULL DEFAULT 0,
  `is_ordered`  TINYINT NOT NULL DEFAULT 0,
  `reason`      VARCHAR(200) DEFAULT NULL,
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_user_time` (`user_id`,`created_at`)
) ENGINE=InnoDB COMMENT='推荐结果日志';

CREATE TABLE `dish_sales_daily` (
  `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
  `merchant_id` BIGINT NOT NULL,
  `dish_id`     BIGINT NOT NULL,
  `stat_date`   DATE NOT NULL,
  `quantity`    INT NOT NULL DEFAULT 0,
  `amount`      DECIMAL(10,2) NOT NULL DEFAULT 0,
  UNIQUE KEY `uk_dish_date` (`dish_id`,`stat_date`),
  KEY `idx_merchant_date` (`merchant_id`,`stat_date`)
) ENGINE=InnoDB COMMENT='菜品日销量';

CREATE TABLE `chat_session` (
  `id`         BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id`    BIGINT NOT NULL,
  `title`      VARCHAR(100) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB COMMENT='客服会话';

CREATE TABLE `chat_message` (
  `id`         BIGINT AUTO_INCREMENT PRIMARY KEY,
  `session_id` BIGINT NOT NULL,
  `role`       VARCHAR(10) NOT NULL,
  `content`    TEXT NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_session` (`session_id`)
) ENGINE=InnoDB COMMENT='客服消息';

CREATE TABLE `notification` (
  `id`         BIGINT AUTO_INCREMENT PRIMARY KEY,
  `user_id`    BIGINT NOT NULL,
  `type`       VARCHAR(30) NOT NULL,
  `title`      VARCHAR(100) NOT NULL,
  `content`    VARCHAR(500) DEFAULT NULL,
  `is_read`    TINYINT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_user_read` (`user_id`,`is_read`)
) ENGINE=InnoDB COMMENT='站内通知';

CREATE TABLE `sys_config` (
  `config_key`   VARCHAR(50) PRIMARY KEY,
  `config_value` VARCHAR(500) NOT NULL,
  `description`  VARCHAR(200) DEFAULT NULL
) ENGINE=InnoDB COMMENT='系统配置';

-- ============ 初始数据 ============
INSERT INTO merchant_category (name, sort) VALUES
('快餐便当',1),('地方菜系',2),('炸鸡汉堡',3),('甜品饮品',4),('麻辣烫/火锅',5),('包子粥铺',6);

INSERT INTO sys_config VALUES
('recommend.weight.rec','0.5','精排推荐分权重'),
('recommend.weight.quality','0.3','精排质量分权重'),
('recommend.weight.bid','0.2','精排竞价分权重'),
('order.timeout.pay.minutes','15','未支付自动取消分钟数'),
('ai.llm.provider','deepseek','LLM服务商 deepseek/dashscope/ollama'),
('ai.llm.api_key','','LLM API Key（管理端配置）'),
('ai.llm.base_url','https://api.deepseek.com','LLM API Base URL'),
('ai.llm.model','deepseek-chat','LLM 模型名'),
('ai.ollama.base_url','http://localhost:11434','Ollama 降级地址'),
('map.amap.js_key','','高德 JS API Key（管理端配置）'),
('map.amap.web_key','','高德 Web 服务 Key（路径规划）');

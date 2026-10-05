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
  -- 省市区表单里不采集，给默认空串；经纬度允许为空（未在地图上选点）
  `province`    VARCHAR(50) NOT NULL DEFAULT '',
  `city`        VARCHAR(50) NOT NULL DEFAULT '',
  `district`    VARCHAR(50) NOT NULL DEFAULT '',
  `detail`      VARCHAR(200) NOT NULL,
  `lng`         DECIMAL(10,6) NULL DEFAULT NULL,
  `lat`         DECIMAL(10,6) NULL DEFAULT NULL,
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
  `delivery_fee`     DECIMAL(10,2) NOT NULL DEFAULT 3.00 COMMENT '商家自填配送费（仅作展示兜底，真实配送费按距离动态计算）',
  `delivery_radius_km` DECIMAL(6,2) DEFAULT NULL COMMENT '配送范围(km)，NULL=用平台默认(sys_config: delivery.radius_km)',
  `package_fee`      DECIMAL(10,2) NOT NULL DEFAULT 1.00,
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
  `client_token`     VARCHAR(64) DEFAULT NULL COMMENT '下单幂等令牌（客户端 UUID）',
  `distance_km`      DECIMAL(8,3) DEFAULT NULL COMMENT '商家到收货地址的距离(km)，下单时快照',
  `merchant_rate`    DECIMAL(5,4) DEFAULT NULL COMMENT '商家抽成比例快照(0~1)',
  `rider_rate`       DECIMAL(5,4) DEFAULT NULL COMMENT '骑手抽成比例快照(0~1)',
  `merchant_income`  DECIMAL(10,2) DEFAULT NULL COMMENT '商家实收',
  `rider_income`     DECIMAL(10,2) DEFAULT NULL COMMENT '骑手配送收入',
  `platform_income`  DECIMAL(10,2) DEFAULT NULL COMMENT '平台抽成收入',
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
  -- 同一用户 + 同一 client_token 只允许一单：防连点/弱网重试产生重复订单
  UNIQUE KEY `uk_user_client_token` (`user_id`,`client_token`),
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
('map.amap.web_key','','高德 Web 服务 Key（路径规划）'),
('delivery.radius_km','5.00','平台默认配送范围(km)，商家未单独设置时生效'),
('delivery.base_fee','3.00','配送起步费(元)'),
('delivery.per_km_fee','1.50','超出免费距离后每公里配送费(元/km)'),
('delivery.free_distance_km','1.00','起步费包含的免费距离(km)'),
('delivery.max_fee','0.00','单笔配送费上限(元)，0 表示不限制'),
('commission.merchant.rate','0.10','平台对商家的抽成比例(0~1)'),
('commission.rider.rate','0.05','平台对骑手的抽成比例(0~1)');

-- ============ AI 模型池（按优先级降级） ============
-- provider: zen=opencode 免费模型(内置门禁,免密钥) / openai=OpenAI 兼容 / ollama=本地 Ollama
-- priority: 数值越小越优先; 高优先级不可用(未启用/未填 Key/调用失败)时自动降级到下一个
CREATE TABLE `ai_model` (
  `id`         BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  `name`       VARCHAR(50)  NOT NULL                COMMENT '显示名称',
  `provider`   VARCHAR(20)  NOT NULL                COMMENT 'zen/openai/ollama',
  `base_url`   VARCHAR(200) DEFAULT NULL            COMMENT 'API 地址',
  `api_key`    VARCHAR(300) DEFAULT NULL            COMMENT '密钥(zen 免密钥可留空)',
  `model_id`   VARCHAR(80)  NOT NULL                COMMENT '模型标识',
  `enabled`    TINYINT      NOT NULL DEFAULT 1      COMMENT '是否启用',
  `priority`   INT          NOT NULL DEFAULT 100    COMMENT '优先级,越小越优先',
  `timeout`    INT          NOT NULL DEFAULT 30     COMMENT '单次调用超时(秒)',
  `remark`     VARCHAR(200) DEFAULT NULL            COMMENT '备注',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY `idx_model_priority` (`enabled`, `priority`)
) ENGINE=InnoDB COMMENT='AI 模型配置';

INSERT INTO ai_model (name, provider, base_url, api_key, model_id, enabled, priority, timeout, remark) VALUES
('OpenCode Zen 免费模型', 'zen',    NULL,                                                   NULL,  'mimo-v2.6-flash-free', 1, 10, 60,  '免密钥; 未开启外网则自动降级'),
('DeepSeek',                 'openai', 'https://api.deepseek.com',                       '',    'deepseek-v4-pro',      1, 20, 30,  '需填写 API Key,留空则跳过; 也可用 deepseek-flash'),
('通义千问',                 'openai', 'https://dashscope.aliyuncs.com/compatible-mode/v1', '', 'qwen-plus',            0, 30, 30,  '需填写 API Key,默认关闭'),
('本地 Ollama',              'ollama', 'http://host.docker.internal:11434',              NULL,  'qwen2.5:7b-instruct',  1, 40, 60,  '本地兜底,需本机已启动 Ollama');

-- ============ 订单会话（用户 ↔ 商家） ============
-- 一个订单对应一个会话；会话内可发文字、图片、订单卡片与售后工单
CREATE TABLE `im_session` (
  `id`               BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  `order_id`         BIGINT       NOT NULL                COMMENT '订单 ID',
  `user_id`          BIGINT       NOT NULL                COMMENT '下单用户',
  `merchant_id`      BIGINT       NOT NULL                COMMENT '商家 ID',
  `status`           VARCHAR(20)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/CLOSED',
  `last_msg`         VARCHAR(200) DEFAULT NULL            COMMENT '最后一条消息摘要',
  `last_msg_at`      DATETIME     DEFAULT NULL            COMMENT '最后消息时间',
  `user_unread`      INT          NOT NULL DEFAULT 0      COMMENT '用户未读数',
  `merchant_unread`  INT          NOT NULL DEFAULT 0      COMMENT '商家未读数',
  `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_order` (`order_id`),
  KEY `idx_user` (`user_id`, `updated_at`),
  KEY `idx_merchant` (`merchant_id`, `updated_at`)
) ENGINE=InnoDB COMMENT='订单会话';

CREATE TABLE `im_message` (
  `id`          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  `session_id`  BIGINT       NOT NULL                COMMENT '会话 ID',
  `sender_role` VARCHAR(16)  NOT NULL                COMMENT 'USER/MERCHANT/SYSTEM',
  `msg_type`    VARCHAR(16)  NOT NULL DEFAULT 'TEXT' COMMENT 'TEXT/IMAGE/ORDER/TICKET',
  `content`     VARCHAR(1000) DEFAULT NULL           COMMENT '文本内容',
  `payload`     JSON         DEFAULT NULL            COMMENT '结构化数据: 图片数组/订单快照/工单信息',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_session` (`session_id`, `id`)
) ENGINE=InnoDB COMMENT='会话消息';

CREATE TABLE `im_ticket` (
  `id`             BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  `session_id`     BIGINT       NOT NULL                COMMENT '会话 ID',
  `order_id`       BIGINT       NOT NULL                COMMENT '订单 ID',
  `user_id`        BIGINT       NOT NULL                COMMENT '申请人',
  `merchant_id`    BIGINT       NOT NULL                COMMENT '受理商家',
  `type`           VARCHAR(20)  NOT NULL                COMMENT 'REFUND退款/COMPENSATE赔偿/REISSUE补发/OTHER其他',
  `amount`         DECIMAL(10,2) NOT NULL DEFAULT 0     COMMENT '申请金额',
  `reason`         VARCHAR(500) NOT NULL                COMMENT '申请原因',
  `images`         JSON         DEFAULT NULL            COMMENT '凭证图片 URL 数组',
  `status`         VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/APPROVED/REJECTED/CLOSED',
  `merchant_reply` VARCHAR(500) DEFAULT NULL            COMMENT '商家处理意见',
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY `idx_merchant_status` (`merchant_id`, `status`),
  KEY `idx_order` (`order_id`),
  KEY `idx_user` (`user_id`, `created_at`)
) ENGINE=InnoDB COMMENT='售后工单';

CREATE TABLE `operation_log` (
  `id`          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  `admin_id`    BIGINT       DEFAULT NULL COMMENT '操作人 user.id（ADMIN）',
  `action`      VARCHAR(64)  NOT NULL     COMMENT '动作码: AUDIT_MERCHANT/AUDIT_RIDER/CHANGE_USER_STATUS/REFUND_ORDER/DELETE_REVIEW/AUDIT_CAMPAIGN',
  `target_type` VARCHAR(32)  DEFAULT NULL COMMENT '目标类型: MERCHANT/RIDER/USER/ORDER/REVIEW/BID_CAMPAIGN',
  `target_id`   BIGINT       DEFAULT NULL COMMENT '目标 ID',
  `detail`      VARCHAR(500) DEFAULT NULL COMMENT '详情（审核结果、退款金额等）',
  `ip`          VARCHAR(64)  DEFAULT NULL COMMENT '操作来源 IP',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  KEY `idx_admin` (`admin_id`),
  KEY `idx_action` (`action`),
  KEY `idx_created` (`created_at`)
) ENGINE=InnoDB COMMENT='管理端操作日志';

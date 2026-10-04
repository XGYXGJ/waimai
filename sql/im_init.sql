-- 订单会话 / 售后工单 建表脚本
-- 用途：已有数据库不想重跑 waimai.sql（会重建全部表）时，单独执行本文件即可。
-- 幂等：表已存在则跳过，可重复执行。
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `im_session` (
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

CREATE TABLE IF NOT EXISTS `im_message` (
  `id`          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  `session_id`  BIGINT       NOT NULL                COMMENT '会话 ID',
  `sender_role` VARCHAR(16)  NOT NULL                COMMENT 'USER/MERCHANT/SYSTEM',
  `msg_type`    VARCHAR(16)  NOT NULL DEFAULT 'TEXT' COMMENT 'TEXT/IMAGE/ORDER/TICKET',
  `content`     VARCHAR(1000) DEFAULT NULL           COMMENT '文本内容',
  `payload`     JSON         DEFAULT NULL            COMMENT '结构化数据: 图片数组/订单快照/工单信息',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_session` (`session_id`, `id`)
) ENGINE=InnoDB COMMENT='会话消息';

CREATE TABLE IF NOT EXISTS `im_ticket` (
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

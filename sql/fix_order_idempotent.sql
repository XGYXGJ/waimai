-- ============================================================
-- 下单幂等：orders.client_token + (user_id, client_token) 唯一索引
-- 目的：连点 / 弱网重试时不会产生两笔订单
-- 幂等：用 information_schema + PREPARE 判断，重复执行安全（无需 DELIMITER）
-- ============================================================
USE `waimai`;

-- 1) 加列 client_token（客户端生成的 UUID，标识「一次下单意图」）
SET @exist_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'orders'
                     AND COLUMN_NAME = 'client_token');
SET @sql := IF(@exist_col = 0,
    'ALTER TABLE `orders` ADD COLUMN `client_token` VARCHAR(64) DEFAULT NULL COMMENT ''下单幂等令牌（客户端 UUID）'' AFTER `user_coupon_id`',
    'SELECT ''[skip] orders.client_token 已存在'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 唯一索引：同一用户 + 同一令牌只允许一笔订单，作为并发下的最终防线
--    （MySQL 唯一索引允许多行 NULL，所以历史数据 / 不带令牌的请求不受影响）
SET @exist_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'orders'
                     AND INDEX_NAME = 'uk_user_client_token');
SET @sql := IF(@exist_idx = 0,
    'ALTER TABLE `orders` ADD UNIQUE KEY `uk_user_client_token` (`user_id`, `client_token`)',
    'SELECT ''[skip] uk_user_client_token 已存在'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 校验
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'client_token';

SELECT INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols, NON_UNIQUE
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND INDEX_NAME = 'uk_user_client_token'
GROUP BY INDEX_NAME, NON_UNIQUE;

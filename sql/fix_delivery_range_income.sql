-- ============================================================
-- 配送范围 + 距离计价 + 平台/商家/骑手收入结算
--
-- 1) merchant.delivery_radius_km  商家自己的配送范围（km）
--    NULL = 使用平台默认范围（sys_config: delivery.radius_km）
-- 2) orders 增加距离快照、双方抽成比例与三方收入快照
--    收入采用「下单时快照」，这样管理端改费率后历史账目不会漂移
-- 3) sys_config 增加配送计价与抽成配置项（管理端「系统参数」可直接改）
--
-- 幂等：全部用 information_schema + PREPARE 判断，重复执行安全。
-- 执行：mysql -uroot -p waimai < sql/fix_delivery_range_income.sql
-- ============================================================
USE `waimai`;

-- ---------- 1) merchant.delivery_radius_km ----------
SET @exist_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'merchant'
                     AND COLUMN_NAME = 'delivery_radius_km');
SET @sql := IF(@exist_col = 0,
    'ALTER TABLE `merchant` ADD COLUMN `delivery_radius_km` DECIMAL(6,2) DEFAULT NULL COMMENT ''配送范围(km)，NULL=用平台默认'' AFTER `delivery_fee`',
    'SELECT ''[skip] merchant.delivery_radius_km 已存在'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 2) orders 的收入/距离快照列 ----------
SET @exist_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'orders'
                     AND COLUMN_NAME = 'distance_km');
SET @sql := IF(@exist_col = 0,
    'ALTER TABLE `orders` ADD COLUMN `distance_km` DECIMAL(8,3) DEFAULT NULL COMMENT ''商家到收货地址的距离(km)，下单时快照'' AFTER `client_token`',
    'SELECT ''[skip] orders.distance_km 已存在'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exist_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'orders'
                     AND COLUMN_NAME = 'merchant_rate');
SET @sql := IF(@exist_col = 0,
    'ALTER TABLE `orders` ADD COLUMN `merchant_rate` DECIMAL(5,4) DEFAULT NULL COMMENT ''商家抽成比例快照(0~1)'' AFTER `distance_km`',
    'SELECT ''[skip] orders.merchant_rate 已存在'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exist_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'orders'
                     AND COLUMN_NAME = 'rider_rate');
SET @sql := IF(@exist_col = 0,
    'ALTER TABLE `orders` ADD COLUMN `rider_rate` DECIMAL(5,4) DEFAULT NULL COMMENT ''骑手抽成比例快照(0~1)'' AFTER `merchant_rate`',
    'SELECT ''[skip] orders.rider_rate 已存在'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exist_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'orders'
                     AND COLUMN_NAME = 'merchant_income');
SET @sql := IF(@exist_col = 0,
    'ALTER TABLE `orders` ADD COLUMN `merchant_income` DECIMAL(10,2) DEFAULT NULL COMMENT ''商家实收'' AFTER `rider_rate`',
    'SELECT ''[skip] orders.merchant_income 已存在'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exist_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'orders'
                     AND COLUMN_NAME = 'rider_income');
SET @sql := IF(@exist_col = 0,
    'ALTER TABLE `orders` ADD COLUMN `rider_income` DECIMAL(10,2) DEFAULT NULL COMMENT ''骑手配送收入'' AFTER `merchant_income`',
    'SELECT ''[skip] orders.rider_income 已存在'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exist_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'orders'
                     AND COLUMN_NAME = 'platform_income');
SET @sql := IF(@exist_col = 0,
    'ALTER TABLE `orders` ADD COLUMN `platform_income` DECIMAL(10,2) DEFAULT NULL COMMENT ''平台抽成收入'' AFTER `rider_income`',
    'SELECT ''[skip] orders.platform_income 已存在'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 3) 平台配置：配送计价 + 抽成比例 ----------
INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `description`) VALUES
('delivery.radius_km',      '5.00', '平台默认配送范围(km)，商家未单独设置时生效'),
('delivery.base_fee',       '3.00', '配送起步费(元)'),
('delivery.per_km_fee',     '1.50', '超出免费距离后每公里配送费(元/km)'),
('delivery.free_distance_km','1.00','起步费包含的免费距离(km)'),
('delivery.max_fee',        '0.00', '单笔配送费上限(元)，0 表示不限制'),
('commission.merchant.rate','0.10', '平台对商家的抽成比例(0~1)'),
('commission.rider.rate',   '0.05', '平台对骑手的抽成比例(0~1)');

-- ---------- 校验 ----------
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'merchant' AND COLUMN_NAME = 'delivery_radius_km';

SELECT COLUMN_NAME, COLUMN_TYPE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders'
  AND COLUMN_NAME IN ('distance_km','merchant_rate','rider_rate',
                      'merchant_income','rider_income','platform_income')
ORDER BY ORDINAL_POSITION;

SELECT config_key, config_value, description FROM sys_config
WHERE config_key LIKE 'delivery.%' OR config_key LIKE 'commission.%'
ORDER BY config_key;

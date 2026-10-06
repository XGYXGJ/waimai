-- ============================================================
-- 订单会话升级为「用户 + 商家 + 骑手」三方频道
-- 目的：im_session 原本只有 user_id / merchant_id 两方，骑手无法参与订单内沟通，
--       而骑手恰恰是最需要联系商家/顾客的角色（找不到店、超时要催单）。
-- 用法：已有数据库执行本文件即可；新库直接跑 sql/waimai.sql（本脚本对已升级的库无副作用）。
-- 幂等：information_schema + PREPARE 判断，重复执行安全（无需 DELIMITER）
-- 模式与 sql/fix_order_idempotent.sql 一致。
-- ============================================================
USE `waimai`;

-- 1) 会话增加骑手维度
SET @exist_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'im_session'
                     AND COLUMN_NAME = 'rider_id');
SET @sql := IF(@exist_col = 0,
    'ALTER TABLE `im_session` ADD COLUMN `rider_id` BIGINT NULL COMMENT ''接单骑手（rider.id），接单前为空'' AFTER `merchant_id`',
    'SELECT ''[skip] im_session.rider_id 已存在'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exist_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'im_session'
                     AND COLUMN_NAME = 'rider_unread');
SET @sql := IF(@exist_col = 0,
    'ALTER TABLE `im_session` ADD COLUMN `rider_unread` INT NOT NULL DEFAULT 0 COMMENT ''骑手未读数'' AFTER `merchant_unread`',
    'SELECT ''[skip] im_session.rider_unread 已存在'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 会话有效期：送达后 30 分钟。送达时间以订单的 delivered_time 为准（服务端校验），
--    close_at 只作为列表页的展示/筛选辅助。
SET @exist_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'im_session'
                     AND COLUMN_NAME = 'close_at');
SET @sql := IF(@exist_col = 0,
    'ALTER TABLE `im_session` ADD COLUMN `close_at` DATETIME NULL COMMENT ''会话失效时间（送达后 30 分钟）'' AFTER `status`',
    'SELECT ''[skip] im_session.close_at 已存在'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) 骑手维度查询索引（骑手端会话列表）
SET @exist_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'im_session'
                     AND INDEX_NAME = 'idx_rider');
SET @sql := IF(@exist_idx = 0,
    'ALTER TABLE `im_session` ADD INDEX `idx_rider` (`rider_id`, `updated_at`)',
    'SELECT ''[skip] im_session.idx_rider 已存在'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4) 历史会话回填：已送达订单 → close_at = 送达时间 + 30 分钟；未送达留空（进行中）
UPDATE `im_session` s
  JOIN `orders` o ON o.`id` = s.`order_id`
  SET s.`close_at` = DATE_ADD(o.`delivered_time`, INTERVAL 30 MINUTE)
  WHERE o.`delivered_time` IS NOT NULL
    AND s.`close_at` IS NULL;

-- 5) 历史会话回填：接单骑手（接单发生在会话创建之后，老会话 rider_id 为空）
UPDATE `im_session` s
  JOIN `orders` o ON o.`id` = s.`order_id`
  SET s.`rider_id` = o.`rider_id`
  WHERE o.`rider_id` IS NOT NULL
    AND s.`rider_id` IS NULL;

-- ------------------------------------------------------------
-- 校验：应能看到 rider_id / rider_unread / close_at 三列与 idx_rider 索引
-- ------------------------------------------------------------
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'im_session'
  AND COLUMN_NAME IN ('rider_id', 'rider_unread', 'close_at')
ORDER BY ORDINAL_POSITION;

SELECT INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols, NON_UNIQUE
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'im_session' AND INDEX_NAME = 'idx_rider'
GROUP BY INDEX_NAME, NON_UNIQUE;

-- 管理端操作日志表（幂等：可重复执行）
-- 用途：审核（商户/骑手/竞价）、退款、封禁、删除违规评价的留痕，对应设计文档第 9 章。
CREATE TABLE IF NOT EXISTS `operation_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `admin_id` BIGINT DEFAULT NULL COMMENT '操作人 user.id（ADMIN）',
  `action` VARCHAR(64) NOT NULL COMMENT '动作码：AUDIT_MERCHANT/AUDIT_RIDER/CHANGE_USER_STATUS/REFUND_ORDER/DELETE_REVIEW/AUDIT_CAMPAIGN',
  `target_type` VARCHAR(32) DEFAULT NULL COMMENT '目标类型：MERCHANT/RIDER/USER/ORDER/REVIEW/BID_CAMPAIGN',
  `target_id` BIGINT DEFAULT NULL COMMENT '目标 ID',
  `detail` VARCHAR(500) DEFAULT NULL COMMENT '详情（如审核结果、退款金额）',
  `ip` VARCHAR(64) DEFAULT NULL COMMENT '操作来源 IP',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_admin` (`admin_id`),
  KEY `idx_action` (`action`),
  KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理端操作日志';

-- AI 模型池独立迁移脚本
-- 适用于：数据库已存在、不想重跑 waimai.sql（会重建全部表）的情况。
-- 全新环境直接执行 waimai.sql 即可，无需跑本文件。
-- 执行：mysql -u root -p waimai < sql/ai_model_init.sql

CREATE TABLE IF NOT EXISTS `ai_model` (
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

-- DeepSeek 官方的模型名与老版本不同：当前有效的是 deepseek-flash / deepseek-v4-pro
-- （实测 deepseek-v4-flash[1M] 之类会被拒：supported API model names are ...）。
-- 老库里如果还是旧名，这里自动纠正一次。
UPDATE ai_model SET model_id = 'deepseek-v4-pro'
 WHERE provider = 'openai' AND model_id IN ('deepseek-chat', 'deepseek-reasoner');

-- 仅在表为空时写入默认模型，避免重复执行产生重复数据
INSERT INTO ai_model (name, provider, base_url, api_key, model_id, enabled, priority, timeout, remark)
SELECT * FROM (
  SELECT 'OpenCode Zen 免费模型' AS name, 'zen' AS provider, NULL AS base_url, NULL AS api_key,
         'mimo-v2.6-flash-free' AS model_id, 1 AS enabled, 10 AS priority, 60 AS timeout,
         '免密钥; 未开启外网则自动降级' AS remark
  UNION ALL SELECT 'DeepSeek',              'openai', 'https://api.deepseek.com',                        '',    'deepseek-v4-pro',     1, 20, 30, '需填写 API Key,留空则跳过; 也可用 deepseek-flash'
  UNION ALL SELECT '通义千问',              'openai', 'https://dashscope.aliyuncs.com/compatible-mode/v1', '', 'qwen-plus',           0, 30, 30, '需填写 API Key,默认关闭'
  UNION ALL SELECT '本地 Ollama',           'ollama', 'http://host.docker.internal:11434',               NULL,  'qwen2.5:7b-instruct', 1, 40, 60, '本地兜底,需本机已启动 Ollama'
) AS t
WHERE NOT EXISTS (SELECT 1 FROM ai_model);

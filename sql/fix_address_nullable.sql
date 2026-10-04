-- ============================================================
-- 修复：提交订单/保存收货地址时报
--   Field 'province' doesn't have a default value
--
-- 背景：用户端地址表单（AddressEditor.vue）只采集 联系人/电话/详细地址 +
-- 地图选点（lng/lat），并不采集省市区。而 address 表把
-- province/city/district 定义成 NOT NULL 且无默认值，
-- lng/lat 也是 NOT NULL。MyBatis-Plus 默认策略会跳过 null 字段不写入 SQL，
-- 所以 province 等列在 INSERT 里根本没出现 -> MySQL 直接报错。
--
-- 处理：省市区退化为「可为空但默认空串」，经纬度改成真正可空
-- （代码注释本来就写着「未在地图上选点时允许只填文字地址」）。
--
-- 幂等：MODIFY 重复执行结果一致，可反复跑。
-- 执行：mysql -uroot -p waimai < sql/fix_address_nullable.sql
-- ============================================================

ALTER TABLE `address`
  MODIFY COLUMN `province` VARCHAR(50)    NOT NULL DEFAULT '' COMMENT '省（可为空串）',
  MODIFY COLUMN `city`     VARCHAR(50)    NOT NULL DEFAULT '' COMMENT '市（可为空串）',
  MODIFY COLUMN `district` VARCHAR(50)    NOT NULL DEFAULT '' COMMENT '区（可为空串）',
  MODIFY COLUMN `lng`      DECIMAL(10, 6) NULL     DEFAULT NULL COMMENT '经度，未选点为空',
  MODIFY COLUMN `lat`      DECIMAL(10, 6) NULL     DEFAULT NULL COMMENT '纬度，未选点为空';

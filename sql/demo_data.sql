-- ============================================================
-- 演示数据（demo data）—— 本地联调 / 答辩演示用
-- ------------------------------------------------------------
-- 设计原则
--   1) 幂等：全部使用显式主键，重复执行不会新增重复行
--   2) 隔离：所有新增行主键落在 1000~1999 区间，
--      与后端 DataInitializer 自动生成的 1~999 行互不干扰，
--      文件末尾给出「一键清理」语句，可完整回滚
--   3) 自洽：订单金额严格按 PricingService 的口径计算
--         商家营业额 = 菜品金额 + 打包费 - 优惠金额
--         商家实收   = 营业额 × (1 - 0.10)
--         骑手实收   = 配送费 × (1 - 0.05)
--         平台收入   = 营业额 + 配送费 - 商家实收 - 骑手实收
--         配送费     = 3.00 + max(0, 距离 - 1.00) × 1.50
--      三方分账之和 == 用户实付，可直接用管理端收益页核对
--   4) 覆盖全部订单状态：待付款/已支付/已接单/待取餐/配送中/已送达/已取消/已退款
--
-- 前置条件
--   1. 已执行 sql/waimai.sql 建表（老库若缺 im_* 三表，先跑 sql/im_init.sql）
--   2. 图片：先跑 `python tools/gen_demo_images.py`
--      （uploads/ 被 .gitignore 忽略，换机器必须重新生成 40 张占位图）
--   完整说明（账号表 / 数据清单 / 图片目录坑 / 清理方式）：见 示例数据说明.md
--
-- 执行方式
--   mysql -uroot -p --default-character-set=utf8mb4 waimai < sql/demo_data.sql
--   或在 Navicat / Workbench 中整段执行
--
-- 统一口令：新增的 9 个账号密码全部是 123456
-- ============================================================
USE waimai;
SET NAMES utf8mb4;

-- ============================================================
-- 1. 账号（口令统一 123456，与 DataInitializer 的 13800000001~03 同口令）
-- ============================================================
INSERT INTO `user` (id, phone, password_hash, nickname, avatar, role, status, created_at) VALUES
(1001, '13800000011', '$2a$10$XglGt0dnjKQpGI7xEy7lce.PVu0kLyFC5DrmFmRKTTFafjxCcjX9e', '周晓雨',   NULL, 'USER',     1, NOW() - INTERVAL 40 DAY),
(1002, '13800000012', '$2a$10$XglGt0dnjKQpGI7xEy7lce.PVu0kLyFC5DrmFmRKTTFafjxCcjX9e', '陈大力',   NULL, 'USER',     1, NOW() - INTERVAL 32 DAY),
(1003, '13800000021', '$2a$10$XglGt0dnjKQpGI7xEy7lce.PVu0kLyFC5DrmFmRKTTFafjxCcjX9e', '川味小馆', NULL, 'MERCHANT', 1, NOW() - INTERVAL 60 DAY),
(1004, '13800000022', '$2a$10$XglGt0dnjKQpGI7xEy7lce.PVu0kLyFC5DrmFmRKTTFafjxCcjX9e', '甜芯奶茶', NULL, 'MERCHANT', 1, NOW() - INTERVAL 55 DAY),
(1005, '13800000023', '$2a$10$XglGt0dnjKQpGI7xEy7lce.PVu0kLyFC5DrmFmRKTTFafjxCcjX9e', '深海寿司', NULL, 'MERCHANT', 1, NOW() - INTERVAL 48 DAY),
(1006, '13800000024', '$2a$10$XglGt0dnjKQpGI7xEy7lce.PVu0kLyFC5DrmFmRKTTFafjxCcjX9e', '老王炸鸡', NULL, 'MERCHANT', 1, NOW() - INTERVAL 2 DAY),
(1007, '13800000031', '$2a$10$XglGt0dnjKQpGI7xEy7lce.PVu0kLyFC5DrmFmRKTTFafjxCcjX9e', '王快跑',   NULL, 'RIDER',    1, NOW() - INTERVAL 35 DAY),
(1008, '13800000032', '$2a$10$XglGt0dnjKQpGI7xEy7lce.PVu0kLyFC5DrmFmRKTTFafjxCcjX9e', '赵闪电',   NULL, 'RIDER',    1, NOW() - INTERVAL 28 DAY),
(1009, '13800000033', '$2a$10$XglGt0dnjKQpGI7xEy7lce.PVu0kLyFC5DrmFmRKTTFafjxCcjX9e', '孙新手',   NULL, 'RIDER',    1, NOW() - INTERVAL 1 DAY)
ON DUPLICATE KEY UPDATE nickname = VALUES(nickname), role = VALUES(role), status = VALUES(status);

-- ============================================================
-- 2. 商家（3 家审核通过营业中 + 1 家待审核，用于测管理端审核流）
-- ============================================================
INSERT INTO merchant (id, user_id, shop_name, category_id, logo, cover, notice, phone, address,
                      lng, lat, business_hours, min_order_amount, delivery_fee,
                      delivery_radius_km, package_fee, rating, monthly_sales,
                      open_status, audit_status, audit_remark, created_at) VALUES
(1001, 1003, '川味小馆', 2, '/uploads/demo/shop-1001-logo.jpg', '/uploads/demo/shop-1001-cover.jpg',
 '招牌水煮牛肉每日限量 30 份，满 50 减 10', '13800000021', '北京市海淀区中关村南大街 12 号',
 116.400100, 39.918200, '10:00-22:00', 20.00, 3.00, 4.00, 1.00, 4.7, 1860, 1, 1, NULL, NOW() - INTERVAL 60 DAY),
(1002, 1004, '甜芯奶茶', 4, '/uploads/demo/shop-1002-logo.jpg', '/uploads/demo/shop-1002-cover.jpg',
 '现制茶饮，第二杯半价（到店）；外卖满 15 起送', '13800000022', '北京市海淀区知春路 27 号 1 层',
 116.392500, 39.914000, '09:00-23:00', 15.00, 2.50, 3.00, 0.50, 4.9, 2430, 1, 1, NULL, NOW() - INTERVAL 55 DAY),
(1003, 1005, '深海寿司', 2, '/uploads/demo/shop-1003-logo.jpg', '/uploads/demo/shop-1003-cover.jpg',
 '刺身每日空运直达，介意路程远请慎拍', '13800000023', '北京市海淀区中关村东路 8 号',
 116.405500, 39.921000, '11:00-21:30', 40.00, 4.00, 5.00, 1.50, 4.6, 720, 1, 1, NULL, NOW() - INTERVAL 48 DAY),
(1004, 1006, '老王炸鸡', 3, '/uploads/demo/shop-1004-logo.jpg', '/uploads/demo/shop-1004-cover.jpg',
 '新店筹备中，审核通过后开卖', '13800000024', '北京市海淀区学院路 30 号',
 116.388000, 39.910000, '10:00-23:00', 25.00, 3.50, 3.00, 1.00, 4.5, 0, 0, 0, NULL, NOW() - INTERVAL 2 DAY)
ON DUPLICATE KEY UPDATE shop_name = VALUES(shop_name), notice = VALUES(notice),
  logo = VALUES(logo), cover = VALUES(cover), rating = VALUES(rating),
  monthly_sales = VALUES(monthly_sales), open_status = VALUES(open_status),
  audit_status = VALUES(audit_status), delivery_radius_km = VALUES(delivery_radius_km);

-- 1 号商家（张记小厨，DataInitializer 建的老数据）原先没有图片，这里补齐
UPDATE merchant SET logo = '/uploads/demo/shop-1-logo.jpg', cover = '/uploads/demo/shop-1-cover.jpg',
       delivery_radius_km = 3.50, package_fee = 1.00
 WHERE id = 1 AND (logo IS NULL OR logo = '');

-- ============================================================
-- 3. 菜品分类（每个商家 3 类）
-- ============================================================
INSERT INTO dish_category (id, merchant_id, name, sort) VALUES
(1001, 1001, '招牌川菜', 1), (1002, 1001, '家常小炒', 2), (1003, 1001, '主食饮品', 3),
(1004, 1002, '招牌奶茶', 1), (1005, 1002, '鲜果茶',   2), (1006, 1002, '小食甜点', 3),
(1007, 1003, '寿司拼盘', 1), (1008, 1003, '刺身',     2), (1009, 1003, '汤品小食', 3),
(1010, 1004, '炸鸡套餐', 1), (1011, 1004, '汉堡',     2), (1012, 1004, '饮品',     3)
ON DUPLICATE KEY UPDATE name = VALUES(name), sort = VALUES(sort);

-- ============================================================
-- 4. 菜品（4 家 × 6 道 = 24 道）
-- ============================================================
INSERT INTO dish (id, merchant_id, category_id, name, description, image, price, original_price,
                  unit, stock, monthly_sales, rating, tags, is_recommend, status, created_at) VALUES
-- 川味小馆（1001）
(1001, 1001, 1001, '水煮牛肉',   '现片黄牛肉，滚油泼花椒，麻辣鲜香', '/uploads/demo/dish-1001.jpg', 38.00, 45.00, '份', 80,  560, 4.9, '招牌,麻辣,下饭', 1, 1, NOW() - INTERVAL 60 DAY),
(1002, 1001, 1001, '麻婆豆腐',   '郫县豆瓣 + 汉源花椒，嫩而不碎',   '/uploads/demo/dish-1002.jpg', 22.00, 26.00, '份', 120, 420, 4.8, '微辣,下饭',     1, 1, NOW() - INTERVAL 60 DAY),
(1003, 1001, 1002, '回锅肉',     '二刀肉配蒜苗，肥而不腻',           '/uploads/demo/dish-1003.jpg', 32.00, 36.00, '份', 90,  350, 4.7, '川味,下饭',     0, 1, NOW() - INTERVAL 58 DAY),
(1004, 1001, 1002, '鱼香肉丝',   '酸甜微辣，配木耳笋丝',             '/uploads/demo/dish-1004.jpg', 26.00, NULL,  '份', 100, 300, 4.7, '家常',          0, 1, NOW() - INTERVAL 58 DAY),
(1005, 1001, 1003, '担担面',     '芽菜肉臊，细面筋道',               '/uploads/demo/dish-1005.jpg', 16.00, NULL,  '碗', 150, 260, 4.6, '面食',          0, 1, NOW() - INTERVAL 50 DAY),
(1006, 1001, 1003, '红糖冰粉',   '手搓冰粉，红糖醪糟，解辣神器',     '/uploads/demo/dish-1006.jpg',  8.00, NULL,  '份', 200, 480, 4.9, '冰爽,解辣',     0, 1, NOW() - INTERVAL 50 DAY),
-- 甜芯奶茶（1002）
(1007, 1002, 1004, '芋泥啵啵奶茶', '手捣芋泥 + 黑糖啵啵，三分糖推荐', '/uploads/demo/dish-1007.jpg', 16.00, 19.00, '杯', 300, 980, 4.9, '招牌,热销',     1, 1, NOW() - INTERVAL 55 DAY),
(1008, 1002, 1004, '生椰拿铁',     '厚椰乳 + 现磨浓缩，冰热可选',     '/uploads/demo/dish-1008.jpg', 18.00, NULL,  '杯', 300, 870, 4.8, '招牌,咖啡',     1, 1, NOW() - INTERVAL 55 DAY),
(1009, 1002, 1005, '杨枝甘露',     '当季芒果 + 西柚粒 + 西米',        '/uploads/demo/dish-1009.jpg', 20.00, 23.00, '杯', 200, 660, 4.9, '鲜果茶',        0, 1, NOW() - INTERVAL 45 DAY),
(1010, 1002, 1005, '满杯百香果',   '整颗百香果现打，微酸清爽',        '/uploads/demo/dish-1010.jpg', 15.00, NULL,  '杯', 250, 540, 4.7, '鲜果茶',        0, 1, NOW() - INTERVAL 45 DAY),
(1011, 1002, 1006, '提拉米苏',     '马斯卡彭 + 手指饼，冷藏出品',      '/uploads/demo/dish-1011.jpg', 22.00, NULL,  '份', 60,  320, 4.8, '甜点',          0, 1, NOW() - INTERVAL 40 DAY),
(1012, 1002, 1006, '芝士蛋挞',     '现烤挞皮，芝士流心',              '/uploads/demo/dish-1012.jpg',  9.00, NULL,  '个', 180, 700, 4.8, '甜点,热销',     0, 1, NOW() - INTERVAL 40 DAY),
-- 深海寿司（1003）
(1013, 1003, 1007, '三文鱼刺身',   '挪威空运，厚切 8 片',             '/uploads/demo/dish-1013.jpg', 68.00, 78.00, '份', 30,  210, 4.9, '招牌,刺身',     1, 1, NOW() - INTERVAL 48 DAY),
(1014, 1003, 1007, '豪华寿司拼盘', '12 贯组合，含玉子与军舰',          '/uploads/demo/dish-1014.jpg', 88.00, NULL,  '份', 25,  160, 4.8, '招牌,拼盘',     1, 1, NOW() - INTERVAL 48 DAY),
(1015, 1003, 1008, '金枪鱼寿司',   '赤身 2 贯，醋饭手捏',             '/uploads/demo/dish-1015.jpg', 32.00, NULL,  '份', 40,  190, 4.7, '寿司',          0, 1, NOW() - INTERVAL 42 DAY),
(1016, 1003, 1008, '鳗鱼饭',       '蒲烧鳗鱼 + 无菌蛋丝',             '/uploads/demo/dish-1016.jpg', 45.00, NULL,  '份', 35,  240, 4.8, '主食',          0, 1, NOW() - INTERVAL 42 DAY),
(1017, 1003, 1009, '味噌汤',       '本枯节高汤，豆腐海带',            '/uploads/demo/dish-1017.jpg', 12.00, NULL,  '碗', 80,  300, 4.6, '汤品',          0, 1, NOW() - INTERVAL 38 DAY),
(1018, 1003, 1009, '日式茶碗蒸',   '蛋液细滑，虾仁香菇',              '/uploads/demo/dish-1018.jpg', 18.00, NULL,  '份', 50,  150, 4.7, '小食',          0, 1, NOW() - INTERVAL 38 DAY),
-- 老王炸鸡（1004，待审核，暂不上架售卖）
(1019, 1004, 1010, '香辣炸鸡桶',   '8 块炸鸡 + 2 份小食，够 2-3 人',  '/uploads/demo/dish-1019.jpg', 49.00, 59.00, '桶', 50,  0, 4.5, '套餐',          1, 1, NOW() - INTERVAL 2 DAY),
(1020, 1004, 1010, '黄金鸡块',     '鸡腿肉手打，外酥里嫩',            '/uploads/demo/dish-1020.jpg', 15.00, NULL,  '份', 100, 0, 4.5, '小食',          0, 1, NOW() - INTERVAL 2 DAY),
(1021, 1004, 1011, '双层牛肉堡',   '双牛肉饼 + 芝士，重口首选',       '/uploads/demo/dish-1021.jpg', 25.00, NULL,  '个', 60,  0, 4.4, '汉堡',          0, 1, NOW() - INTERVAL 2 DAY),
(1022, 1004, 1011, '香辣鸡腿堡',   '整块鸡腿排，微辣',                '/uploads/demo/dish-1022.jpg', 18.00, NULL,  '个', 80,  0, 4.5, '汉堡',          0, 1, NOW() - INTERVAL 2 DAY),
(1023, 1004, 1012, '可乐（中杯）', '冰镇可口可乐',                    '/uploads/demo/dish-1023.jpg',  7.00, NULL,  '杯', 200, 0, 4.6, '饮品',          0, 1, NOW() - INTERVAL 2 DAY),
(1024, 1004, 1012, '黄金薯条',     '粗切薯条，撒海苔盐',              '/uploads/demo/dish-1024.jpg', 12.00, NULL,  '份', 150, 0, 4.6, '小食',          0, 1, NOW() - INTERVAL 2 DAY)
ON DUPLICATE KEY UPDATE name = VALUES(name), price = VALUES(price),
  image = VALUES(image), merchant_id = VALUES(merchant_id),
  category_id = VALUES(category_id), monthly_sales = VALUES(monthly_sales),
  is_recommend = VALUES(is_recommend), status = VALUES(status);

-- 给 1 号商家（张记小厨）的 6 道菜补图
UPDATE dish SET image = '/uploads/demo/dish-1.jpg' WHERE id = 1 AND (image IS NULL OR image = '');
UPDATE dish SET image = '/uploads/demo/dish-2.jpg' WHERE id = 2 AND (image IS NULL OR image = '');
UPDATE dish SET image = '/uploads/demo/dish-3.jpg' WHERE id = 3 AND (image IS NULL OR image = '');
UPDATE dish SET image = '/uploads/demo/dish-4.jpg' WHERE id = 4 AND (image IS NULL OR image = '');
UPDATE dish SET image = '/uploads/demo/dish-5.jpg' WHERE id = 5 AND (image IS NULL OR image = '');
UPDATE dish SET image = '/uploads/demo/dish-6.jpg' WHERE id = 6 AND (image IS NULL OR image = '');

-- ============================================================
-- 5. 骑手（2 名审核通过 + 1 名待审核）
-- ============================================================
INSERT INTO rider (id, user_id, real_name, phone, vehicle, audit_status, work_status, today_orders, created_at) VALUES
(1,    4,    '李小骑', '13800000003', '电动车', 1, 1, 2, NOW() - INTERVAL 70 DAY),
(1001, 1007, '王快跑', '13800000031', '电动车', 1, 1, 3, NOW() - INTERVAL 35 DAY),
(1002, 1008, '赵闪电', '13800000032', '摩托车', 1, 2, 7, NOW() - INTERVAL 28 DAY),
(1003, 1009, '孙新手', '13800000033', '电动车', 0, 0, 0, NOW() - INTERVAL 1 DAY)
ON DUPLICATE KEY UPDATE real_name = VALUES(real_name), vehicle = VALUES(vehicle),
  audit_status = VALUES(audit_status), work_status = VALUES(work_status),
  today_orders = VALUES(today_orders);

-- ============================================================
-- 6. 收货地址（覆盖 3 个用户，含经纬度以便测距离计价）
-- ============================================================
INSERT INTO address (id, user_id, contact, phone, gender, province, city, district, detail, lng, lat, tag, is_default, created_at) VALUES
(1001, 2,    '张伟',   '13800000001', 1, '北京市', '北京市', '海淀区', '中关村南大街 5 号院 3 号楼 502', 116.397500, 39.917000, '家',   1, NOW() - INTERVAL 30 DAY),
(1002, 1001, '周晓雨', '13800000011', 2, '北京市', '北京市', '海淀区', '知春路 27 号科技楼 12 层',        116.394000, 39.915500, '公司', 1, NOW() - INTERVAL 25 DAY),
(1003, 1001, '周晓雨', '13800000011', 2, '北京市', '北京市', '朝阳区', '建国路 88 号 SOHO 现代城 B 座',   116.462000, 39.909000, '学校', 0, NOW() - INTERVAL 20 DAY),
(1004, 1002, '陈大力', '13800000012', 1, '北京市', '北京市', '海淀区', '中关村大街 1 号海龙大厦 8 层',    116.397100, 39.916500, '公司', 1, NOW() - INTERVAL 18 DAY)
ON DUPLICATE KEY UPDATE contact = VALUES(contact), detail = VALUES(detail),
  lng = VALUES(lng), lat = VALUES(lat), is_default = VALUES(is_default);

-- ============================================================
-- 7. 优惠券模板 + 用户持券
-- ============================================================
INSERT INTO coupon (id, merchant_id, name, type, threshold_amount, discount_amount, discount_rate,
                    total_count, received_count, per_user_limit, start_time, end_time, status, created_at) VALUES
(1001, 0,    '新人无门槛立减券', 3, 0.00,  5.00, NULL, 10000, 3260, 1, NOW() - INTERVAL 60 DAY, NOW() + INTERVAL 60 DAY, 1, NOW() - INTERVAL 60 DAY),
(1002, 0,    '平台满 30 减 8',   1, 30.00, 8.00, NULL, 5000,  1280, 1, NOW() - INTERVAL 45 DAY, NOW() + INTERVAL 45 DAY, 1, NOW() - INTERVAL 45 DAY),
(1003, 1001, '川味小馆满 50 减 10', 1, 50.00, 10.00, NULL, 800, 310, 2, NOW() - INTERVAL 30 DAY, NOW() + INTERVAL 30 DAY, 1, NOW() - INTERVAL 30 DAY),
(1004, 1002, '甜芯奶茶 8 折券',   2, 20.00, NULL, 0.80,  600, 220, 1, NOW() - INTERVAL 25 DAY, NOW() + INTERVAL 25 DAY, 1, NOW() - INTERVAL 25 DAY),
(1005, 1003, '深海寿司满 60 减 20', 1, 60.00, 20.00, NULL, 300,  86, 1, NOW() - INTERVAL 20 DAY, NOW() + INTERVAL 20 DAY, 1, NOW() - INTERVAL 20 DAY),
(1006, 0,    '已结束的端午券',   1, 20.00, 6.00, NULL, 2000, 2000, 1, NOW() - INTERVAL 120 DAY, NOW() - INTERVAL 90 DAY, 0, NOW() - INTERVAL 120 DAY)
ON DUPLICATE KEY UPDATE name = VALUES(name), status = VALUES(status),
  start_time = VALUES(start_time), end_time = VALUES(end_time),
  threshold_amount = VALUES(threshold_amount), discount_amount = VALUES(discount_amount),
  discount_rate = VALUES(discount_rate);

-- 用户持券：0 未使用 / 1 已使用 / 2 已过期（与订单号对应，可交叉验证）
INSERT INTO user_coupon (id, coupon_id, user_id, status, received_at, used_at, order_id) VALUES
(1001, 1001, 2,    0, NOW() - INTERVAL 20 DAY, NULL,                        NULL),
(1002, 1002, 2,    1, NOW() - INTERVAL 6 DAY,  NOW() - INTERVAL 6 DAY,      1001),
(1003, 1004, 2,    1, NOW() - INTERVAL 4 DAY,  NOW() - INTERVAL 3 DAY,      1003),
(1004, 1001, 1001, 0, NOW() - INTERVAL 15 DAY, NULL,                        NULL),
(1005, 1005, 1001, 1, NOW() - INTERVAL 2 DAY,  NOW() - INTERVAL 45 MINUTE,  1004),
(1006, 1003, 1001, 0, NOW() - INTERVAL 10 DAY, NULL,                        NULL),
(1007, 1006, 2,    2, NOW() - INTERVAL 100 DAY, NULL,                       NULL)
ON DUPLICATE KEY UPDATE status = VALUES(status), used_at = VALUES(used_at), order_id = VALUES(order_id);

-- ============================================================
-- 8. 订单（覆盖 8 种状态；金额与分账严格按 PricingService 口径）
-- ============================================================
INSERT INTO orders (id, order_no, user_id, merchant_id, rider_id, address_snapshot,
                    dish_amount, delivery_fee, package_fee, discount_amount, pay_amount, user_coupon_id,
                    client_token, distance_km, merchant_rate, rider_rate,
                    merchant_income, rider_income, platform_income,
                    status, remark, pay_time, accept_time, pickup_time, delivered_time,
                    cancel_reason, cancel_by, created_at) VALUES
-- 1001 已送达（6 天前，已评价）：50 + 3.30 + 1.00 - 8.00 = 46.30
(1001, 'DM202609290001', 2, 1, 1001,
 '{"contact":"张伟","phone":"13800000001","detail":"北京市北京市海淀区中关村南大街 5 号院 3 号楼 502","lng":116.3975,"lat":39.917}',
 50.00, 3.30, 1.00, 8.00, 46.30, 1002, 'demo-token-1001', 1.200, 0.1000, 0.0500, 38.70, 3.14, 4.46,
 'DELIVERED', '不要香菜', NOW() - INTERVAL 6 DAY + INTERVAL 2 MINUTE, NOW() - INTERVAL 6 DAY + INTERVAL 5 MINUTE,
 NOW() - INTERVAL 6 DAY + INTERVAL 18 MINUTE, NOW() - INTERVAL 6 DAY + INTERVAL 35 MINUTE, NULL, NULL, NOW() - INTERVAL 6 DAY),
-- 1002 已送达（4 天前，未评价）：54 + 3.00 + 1.00 = 58.00
(1002, 'DM202610010002', 1001, 1001, 1002,
 '{"contact":"周晓雨","phone":"13800000011","detail":"北京市北京市海淀区知春路 27 号科技楼 12 层","lng":116.394,"lat":39.9155}',
 54.00, 3.00, 1.00, 0.00, 58.00, NULL, 'demo-token-1002', 0.350, 0.1000, 0.0500, 49.50, 2.85, 5.65,
 'DELIVERED', NULL, NOW() - INTERVAL 4 DAY + INTERVAL 1 MINUTE, NOW() - INTERVAL 4 DAY + INTERVAL 3 MINUTE,
 NOW() - INTERVAL 4 DAY + INTERVAL 15 MINUTE, NOW() - INTERVAL 4 DAY + INTERVAL 28 MINUTE, NULL, NULL, NOW() - INTERVAL 4 DAY),
-- 1003 已送达（3 天前，8 折券）：50 + 4.65 + 0.50 - 10.00 = 45.15
(1003, 'DM202610020003', 2, 1002, 1001,
 '{"contact":"张伟","phone":"13800000001","detail":"北京市北京市海淀区中关村南大街 5 号院 3 号楼 502","lng":116.3975,"lat":39.917}',
 50.00, 4.65, 0.50, 10.00, 45.15, 1003, 'demo-token-1003', 2.100, 0.1000, 0.0500, 36.45, 4.42, 4.28,
 'DELIVERED', '奶茶三分糖，蛋挞要热的', NOW() - INTERVAL 3 DAY + INTERVAL 1 MINUTE, NOW() - INTERVAL 3 DAY + INTERVAL 4 MINUTE,
 NOW() - INTERVAL 3 DAY + INTERVAL 14 MINUTE, NOW() - INTERVAL 3 DAY + INTERVAL 25 MINUTE, NULL, NULL, NOW() - INTERVAL 3 DAY),
-- 1004 配送中（45 分钟前，满 60 减 20）
(1004, 'DM202610050004', 1001, 1003, 1002,
 '{"contact":"周晓雨","phone":"13800000011","detail":"北京市北京市海淀区知春路 27 号科技楼 12 层","lng":116.394,"lat":39.9155}',
 80.00, 4.20, 1.50, 20.00, 65.70, 1005, 'demo-token-1004', 1.800, 0.1000, 0.0500, 55.35, 3.99, 6.36,
 'DELIVERING', '多给两包芥末', NOW() - INTERVAL 45 MINUTE + INTERVAL 1 MINUTE, NOW() - INTERVAL 45 MINUTE + INTERVAL 3 MINUTE,
 NOW() - INTERVAL 45 MINUTE + INTERVAL 8 MINUTE, NULL, NULL, NULL, NOW() - INTERVAL 45 MINUTE),
-- 1005 待取餐（20 分钟前，等待骑手接单）
(1005, 'DM202610050005', 2, 1, NULL,
 '{"contact":"张伟","phone":"13800000001","detail":"北京市北京市海淀区中关村南大街 5 号院 3 号楼 502","lng":116.3975,"lat":39.917}',
 36.00, 3.30, 1.00, 0.00, 40.30, NULL, 'demo-token-1005', 1.200, 0.1000, 0.0500, 33.30, 3.14, 3.86,
 'WAITING_PICKUP', NULL, NOW() - INTERVAL 20 MINUTE + INTERVAL 1 MINUTE, NOW() - INTERVAL 20 MINUTE + INTERVAL 4 MINUTE,
 NULL, NULL, NULL, NULL, NOW() - INTERVAL 20 MINUTE),
-- 1006 已接单（12 分钟前）
(1006, 'DM202610050006', 1001, 1002, NULL,
 '{"contact":"周晓雨","phone":"13800000011","detail":"北京市北京市朝阳区建国路 88 号 SOHO 现代城 B 座","lng":116.462,"lat":39.909}',
 58.00, 4.65, 0.50, 0.00, 63.15, NULL, 'demo-token-1006', 2.100, 0.1000, 0.0500, 52.65, 4.42, 6.08,
 'ACCEPTED', NULL, NOW() - INTERVAL 12 MINUTE + INTERVAL 1 MINUTE, NOW() - INTERVAL 12 MINUTE + INTERVAL 2 MINUTE,
 NULL, NULL, NULL, NULL, NOW() - INTERVAL 12 MINUTE),
-- 1007 待付款（5 分钟前，可用于演示 15 分钟未支付自动取消）
(1007, 'DM202610050007', 1002, 1001, NULL,
 '{"contact":"陈大力","phone":"13800000012","detail":"北京市北京市海淀区中关村大街 1 号海龙大厦 8 层","lng":116.3971,"lat":39.9165}',
 48.00, 3.00, 1.00, 0.00, 52.00, NULL, 'demo-token-1007', 0.350, 0.1000, 0.0500, 44.10, 2.85, 5.05,
 'PENDING_PAYMENT', '放门口，电话联系', NULL, NULL, NULL, NULL, NULL, NULL, NOW() - INTERVAL 5 MINUTE),
-- 1008 已支付待接单（8 分钟前）
(1008, 'DM202610050008', 2, 1003, NULL,
 '{"contact":"张伟","phone":"13800000001","detail":"北京市北京市海淀区中关村南大街 5 号院 3 号楼 502","lng":116.3975,"lat":39.917}',
 88.00, 4.20, 1.50, 0.00, 93.70, NULL, 'demo-token-1008', 1.800, 0.1000, 0.0500, 80.55, 3.99, 9.16,
 'PAID', NULL, NOW() - INTERVAL 8 MINUTE + INTERVAL 1 MINUTE, NULL, NULL, NULL, NULL, NULL, NOW() - INTERVAL 8 MINUTE),
-- 1009 已取消（2 天前，用户取消）
(1009, 'DM202610030009', 1001, 1, NULL,
 '{"contact":"周晓雨","phone":"13800000011","detail":"北京市北京市海淀区知春路 27 号科技楼 12 层","lng":116.394,"lat":39.9155}',
 15.00, 3.30, 1.00, 0.00, 19.30, NULL, 'demo-token-1009', 1.200, 0.1000, 0.0500, 14.40, 3.14, 1.76,
 'CANCELLED', NULL, NULL, NULL, NULL, NULL, '临时有事，不吃了', 'USER', NOW() - INTERVAL 2 DAY),
-- 1010 已退款（1 天前，商家侧退款）
(1010, 'DM202610040010', 1002, 1002, NULL,
 '{"contact":"陈大力","phone":"13800000012","detail":"北京市北京市海淀区中关村大街 1 号海龙大厦 8 层","lng":116.3971,"lat":39.9165}',
 40.00, 4.65, 0.50, 0.00, 45.15, NULL, 'demo-token-1010', 2.100, 0.1000, 0.0500, 36.45, 4.42, 4.28,
 'REFUNDED', NULL, NOW() - INTERVAL 1 DAY + INTERVAL 1 MINUTE, NULL, NULL, NULL,
 '奶茶机故障，已全额退款', 'MERCHANT', NOW() - INTERVAL 1 DAY),
-- 1011 今日已送达（3 小时前）
(1011, 'DM202610050011', 2, 1002, 1001,
 '{"contact":"张伟","phone":"13800000001","detail":"北京市北京市海淀区中关村南大街 5 号院 3 号楼 502","lng":116.3975,"lat":39.917}',
 39.00, 4.65, 0.50, 0.00, 44.15, NULL, 'demo-token-1011', 2.100, 0.1000, 0.0500, 35.55, 4.42, 4.18,
 'DELIVERED', NULL, NOW() - INTERVAL 3 HOUR + INTERVAL 1 MINUTE, NOW() - INTERVAL 3 HOUR + INTERVAL 3 MINUTE,
 NOW() - INTERVAL 3 HOUR + INTERVAL 13 MINUTE, NOW() - INTERVAL 3 HOUR + INTERVAL 30 MINUTE, NULL, NULL, NOW() - INTERVAL 3 HOUR),
-- 1012 今日已送达（2 小时前）
(1012, 'DM202610050012', 1001, 1001, 1002,
 '{"contact":"周晓雨","phone":"13800000011","detail":"北京市北京市海淀区知春路 27 号科技楼 12 层","lng":116.394,"lat":39.9155}',
 56.00, 3.00, 1.00, 0.00, 60.00, NULL, 'demo-token-1012', 0.350, 0.1000, 0.0500, 51.30, 2.85, 5.85,
 'DELIVERED', NULL, NOW() - INTERVAL 2 HOUR + INTERVAL 1 MINUTE, NOW() - INTERVAL 2 HOUR + INTERVAL 4 MINUTE,
 NOW() - INTERVAL 2 HOUR + INTERVAL 12 MINUTE, NOW() - INTERVAL 2 HOUR + INTERVAL 26 MINUTE, NULL, NULL, NOW() - INTERVAL 2 HOUR)
ON DUPLICATE KEY UPDATE status = VALUES(status), rider_id = VALUES(rider_id),
  pay_amount = VALUES(pay_amount), delivery_fee = VALUES(delivery_fee),
  merchant_income = VALUES(merchant_income), rider_income = VALUES(rider_income),
  platform_income = VALUES(platform_income), distance_km = VALUES(distance_km),
  pay_time = VALUES(pay_time), accept_time = VALUES(accept_time),
  pickup_time = VALUES(pickup_time), delivered_time = VALUES(delivered_time),
  cancel_reason = VALUES(cancel_reason), cancel_by = VALUES(cancel_by),
  -- created_at 必须一起刷新：所有时间都写成「NOW() - x 分钟/天」的相对值，
  -- 重跑脚本时若不刷新，1007 会以过期的创建时间复活，被后端「超时未支付自动取消」任务
  -- 立刻（下一分钟）取消，看不到演示效果。刷新后它重新变回「5 分钟前的待付款订单」。
  created_at = VALUES(created_at);

-- 订单明细
INSERT INTO order_item (id, order_id, dish_id, dish_name, image, price, quantity) VALUES
(1001, 1001, 1,    '招牌红烧肉饭',   '/uploads/demo/dish-1.jpg',    28.00, 1),
(1002, 1001, 2,    '宫保鸡丁饭',     '/uploads/demo/dish-2.jpg',    22.00, 1),
(1003, 1002, 1001, '水煮牛肉',       '/uploads/demo/dish-1001.jpg', 38.00, 1),
(1004, 1002, 1006, '红糖冰粉',       '/uploads/demo/dish-1006.jpg',  8.00, 2),
(1005, 1003, 1007, '芋泥啵啵奶茶',   '/uploads/demo/dish-1007.jpg', 16.00, 2),
(1006, 1003, 1012, '芝士蛋挞',       '/uploads/demo/dish-1012.jpg',  9.00, 2),
(1007, 1004, 1013, '三文鱼刺身',     '/uploads/demo/dish-1013.jpg', 68.00, 1),
(1008, 1004, 1017, '味噌汤',         '/uploads/demo/dish-1017.jpg', 12.00, 1),
(1009, 1005, 3,    '扬州炒饭',       '/uploads/demo/dish-3.jpg',    18.00, 2),
(1010, 1006, 1008, '生椰拿铁',       '/uploads/demo/dish-1008.jpg', 18.00, 2),
(1011, 1006, 1011, '提拉米苏',       '/uploads/demo/dish-1011.jpg', 22.00, 1),
(1012, 1007, 1003, '回锅肉',         '/uploads/demo/dish-1003.jpg', 32.00, 1),
(1013, 1007, 1005, '担担面',         '/uploads/demo/dish-1005.jpg', 16.00, 1),
(1014, 1008, 1014, '豪华寿司拼盘',   '/uploads/demo/dish-1014.jpg', 88.00, 1),
(1015, 1009, 4,    '番茄鸡蛋面',     '/uploads/demo/dish-4.jpg',    15.00, 1),
(1016, 1010, 1009, '杨枝甘露',       '/uploads/demo/dish-1009.jpg', 20.00, 2),
(1017, 1011, 1010, '满杯百香果',     '/uploads/demo/dish-1010.jpg', 15.00, 2),
(1018, 1011, 1012, '芝士蛋挞',       '/uploads/demo/dish-1012.jpg',  9.00, 1),
(1019, 1012, 1002, '麻婆豆腐',       '/uploads/demo/dish-1002.jpg', 22.00, 1),
(1020, 1012, 1004, '鱼香肉丝',       '/uploads/demo/dish-1004.jpg', 26.00, 1),
(1021, 1012, 1006, '红糖冰粉',       '/uploads/demo/dish-1006.jpg',  8.00, 1)
ON DUPLICATE KEY UPDATE dish_name = VALUES(dish_name), price = VALUES(price),
  quantity = VALUES(quantity), image = VALUES(image);

-- 状态流转日志（用户端「订单跟踪」时间轴读的就是这张表）
INSERT INTO order_status_log (id, order_id, from_status, to_status, operator, created_at) VALUES
(1001, 1001, NULL,             'PENDING_PAYMENT', 'user:2',               NOW() - INTERVAL 6 DAY),
(1002, 1001, 'PENDING_PAYMENT','PAID',            'user:2',               NOW() - INTERVAL 6 DAY + INTERVAL 2 MINUTE),
(1003, 1001, 'PAID',           'ACCEPTED',        'merchant:3',           NOW() - INTERVAL 6 DAY + INTERVAL 5 MINUTE),
(1004, 1001, 'ACCEPTED',       'WAITING_PICKUP',  'merchant:3',           NOW() - INTERVAL 6 DAY + INTERVAL 18 MINUTE),
(1005, 1001, 'WAITING_PICKUP', 'DELIVERING',      'rider:1001',           NOW() - INTERVAL 6 DAY + INTERVAL 22 MINUTE),
(1006, 1001, 'DELIVERING',     'DELIVERED',       'rider:1001',           NOW() - INTERVAL 6 DAY + INTERVAL 35 MINUTE),
(1007, 1002, NULL,             'PENDING_PAYMENT', 'user:1001',            NOW() - INTERVAL 4 DAY),
(1008, 1002, 'PENDING_PAYMENT','PAID',            'user:1001',            NOW() - INTERVAL 4 DAY + INTERVAL 1 MINUTE),
(1009, 1002, 'PAID',           'ACCEPTED',        'merchant:1003',        NOW() - INTERVAL 4 DAY + INTERVAL 3 MINUTE),
(1010, 1002, 'ACCEPTED',       'WAITING_PICKUP',  'merchant:1003',        NOW() - INTERVAL 4 DAY + INTERVAL 15 MINUTE),
(1011, 1002, 'WAITING_PICKUP', 'DELIVERING',      'rider:1002',           NOW() - INTERVAL 4 DAY + INTERVAL 20 MINUTE),
(1012, 1002, 'DELIVERING',     'DELIVERED',       'rider:1002',           NOW() - INTERVAL 4 DAY + INTERVAL 28 MINUTE),
(1013, 1003, NULL,             'PENDING_PAYMENT', 'user:2',               NOW() - INTERVAL 3 DAY),
(1014, 1003, 'PENDING_PAYMENT','PAID',            'user:2',               NOW() - INTERVAL 3 DAY + INTERVAL 1 MINUTE),
(1015, 1003, 'PAID',           'ACCEPTED',        'merchant:1004',        NOW() - INTERVAL 3 DAY + INTERVAL 4 MINUTE),
(1016, 1003, 'ACCEPTED',       'WAITING_PICKUP',  'merchant:1004',        NOW() - INTERVAL 3 DAY + INTERVAL 14 MINUTE),
(1017, 1003, 'WAITING_PICKUP', 'DELIVERING',      'rider:1001',           NOW() - INTERVAL 3 DAY + INTERVAL 18 MINUTE),
(1018, 1003, 'DELIVERING',     'DELIVERED',       'rider:1001',           NOW() - INTERVAL 3 DAY + INTERVAL 25 MINUTE),
(1019, 1004, NULL,             'PENDING_PAYMENT', 'user:1001',            NOW() - INTERVAL 45 MINUTE),
(1020, 1004, 'PENDING_PAYMENT','PAID',            'user:1001',            NOW() - INTERVAL 45 MINUTE + INTERVAL 1 MINUTE),
(1021, 1004, 'PAID',           'ACCEPTED',        'merchant:1005',        NOW() - INTERVAL 45 MINUTE + INTERVAL 3 MINUTE),
(1022, 1004, 'ACCEPTED',       'WAITING_PICKUP',  'merchant:1005',        NOW() - INTERVAL 45 MINUTE + INTERVAL 8 MINUTE),
(1023, 1004, 'WAITING_PICKUP', 'DELIVERING',      'rider:1002',           NOW() - INTERVAL 45 MINUTE + INTERVAL 25 MINUTE),
(1024, 1005, NULL,             'PENDING_PAYMENT', 'user:2',               NOW() - INTERVAL 20 MINUTE),
(1025, 1005, 'PENDING_PAYMENT','PAID',            'user:2',               NOW() - INTERVAL 20 MINUTE + INTERVAL 1 MINUTE),
(1026, 1005, 'PAID',           'ACCEPTED',        'merchant:3',           NOW() - INTERVAL 20 MINUTE + INTERVAL 4 MINUTE),
(1027, 1005, 'ACCEPTED',       'WAITING_PICKUP',  'merchant:3',           NOW() - INTERVAL 20 MINUTE + INTERVAL 12 MINUTE),
(1028, 1006, NULL,             'PENDING_PAYMENT', 'user:1001',            NOW() - INTERVAL 12 MINUTE),
(1029, 1006, 'PENDING_PAYMENT','PAID',            'user:1001',            NOW() - INTERVAL 12 MINUTE + INTERVAL 1 MINUTE),
(1030, 1006, 'PAID',           'ACCEPTED',        'merchant:1004',        NOW() - INTERVAL 12 MINUTE + INTERVAL 2 MINUTE),
(1031, 1007, NULL,             'PENDING_PAYMENT', 'user:1002',            NOW() - INTERVAL 5 MINUTE),
(1032, 1008, NULL,             'PENDING_PAYMENT', 'user:2',               NOW() - INTERVAL 8 MINUTE),
(1033, 1008, 'PENDING_PAYMENT','PAID',            'user:2',               NOW() - INTERVAL 8 MINUTE + INTERVAL 1 MINUTE),
(1034, 1009, NULL,             'PENDING_PAYMENT', 'user:1001',            NOW() - INTERVAL 2 DAY),
(1035, 1009, 'PENDING_PAYMENT','CANCELLED',       'user:1001',            NOW() - INTERVAL 2 DAY + INTERVAL 3 MINUTE),
(1036, 1010, NULL,             'PENDING_PAYMENT', 'user:1002',            NOW() - INTERVAL 1 DAY),
(1037, 1010, 'PENDING_PAYMENT','PAID',            'user:1002',            NOW() - INTERVAL 1 DAY + INTERVAL 1 MINUTE),
(1038, 1010, 'PAID',           'REFUNDED',        'merchant:1004',        NOW() - INTERVAL 1 DAY + INTERVAL 6 MINUTE),
(1039, 1011, NULL,             'PENDING_PAYMENT', 'user:2',               NOW() - INTERVAL 3 HOUR),
(1040, 1011, 'PENDING_PAYMENT','PAID',            'user:2',               NOW() - INTERVAL 3 HOUR + INTERVAL 1 MINUTE),
(1041, 1011, 'PAID',           'ACCEPTED',        'merchant:1004',        NOW() - INTERVAL 3 HOUR + INTERVAL 3 MINUTE),
(1042, 1011, 'ACCEPTED',       'WAITING_PICKUP',  'merchant:1004',        NOW() - INTERVAL 3 HOUR + INTERVAL 13 MINUTE),
(1043, 1011, 'WAITING_PICKUP', 'DELIVERING',      'rider:1001',           NOW() - INTERVAL 3 HOUR + INTERVAL 17 MINUTE),
(1044, 1011, 'DELIVERING',     'DELIVERED',       'rider:1001',           NOW() - INTERVAL 3 HOUR + INTERVAL 30 MINUTE),
(1045, 1012, NULL,             'PENDING_PAYMENT', 'user:1001',            NOW() - INTERVAL 2 HOUR),
(1046, 1012, 'PENDING_PAYMENT','PAID',            'user:1001',            NOW() - INTERVAL 2 HOUR + INTERVAL 1 MINUTE),
(1047, 1012, 'PAID',           'ACCEPTED',        'merchant:3',           NOW() - INTERVAL 2 HOUR + INTERVAL 4 MINUTE),
(1048, 1012, 'ACCEPTED',       'WAITING_PICKUP',  'merchant:3',           NOW() - INTERVAL 2 HOUR + INTERVAL 12 MINUTE),
(1049, 1012, 'WAITING_PICKUP', 'DELIVERING',      'rider:1002',           NOW() - INTERVAL 2 HOUR + INTERVAL 16 MINUTE),
(1050, 1012, 'DELIVERING',     'DELIVERED',       'rider:1002',           NOW() - INTERVAL 2 HOUR + INTERVAL 26 MINUTE)
ON DUPLICATE KEY UPDATE to_status = VALUES(to_status), created_at = VALUES(created_at);

-- ============================================================
-- 9. 评价（sentiment/score 取值与 waimai-ai 的 POS/NEU/NEG 一致）
-- ============================================================
INSERT INTO review (id, order_id, user_id, merchant_id, rating, content, images, sentiment, sentiment_score, reply, created_at) VALUES
(1001, 1001, 2,    1,    5, '红烧肉太香了，肥而不腻，骑手 35 分钟就送到了！',   NULL, 'POS', 0.982, '谢谢支持，欢迎常来~',            NOW() - INTERVAL 6 DAY + INTERVAL 50 MINUTE),
(1002, 1002, 1001, 1001, 4, '水煮牛肉够味，冰粉解辣，就是出餐稍微慢了点。',     NULL, 'POS', 0.712, NULL,                            NOW() - INTERVAL 4 DAY + INTERVAL 40 MINUTE),
(1003, 1003, 2,    1002, 5, '奶茶料很足，蛋挞是现烤的，下次还点这家。',         NULL, 'POS', 0.951, '感谢喜欢，下次试试生椰拿铁~',    NOW() - INTERVAL 3 DAY + INTERVAL 35 MINUTE),
(1004, 1011, 2,    1002, 3, '百香果有点偏酸，杯盖还漏了一点，包装一般。',       NULL, 'NEU', 0.545, '抱歉体验不佳，已反馈制作组',      NOW() - INTERVAL 3 HOUR + INTERVAL 40 MINUTE),
(1005, 1012, 1001, 1001, 5, '麻婆豆腐下饭神器，冰粉免费送的量也足。',           NULL, 'POS', 0.990, NULL,                            NOW() - INTERVAL 2 HOUR + INTERVAL 35 MINUTE)
ON DUPLICATE KEY UPDATE rating = VALUES(rating), content = VALUES(content),
  sentiment = VALUES(sentiment), sentiment_score = VALUES(sentiment_score), reply = VALUES(reply);

-- ============================================================
-- 10. 收藏 / 搜索历史 / 购物车
-- ============================================================
INSERT INTO favorite (id, user_id, merchant_id, created_at) VALUES
(1001, 2,    1,    NOW() - INTERVAL 10 DAY),
(1002, 2,    1002, NOW() - INTERVAL 3 DAY),
(1003, 1001, 1001, NOW() - INTERVAL 20 DAY),
(1004, 1002, 1001, NOW() - INTERVAL 5 DAY)
ON DUPLICATE KEY UPDATE created_at = VALUES(created_at);

INSERT INTO search_history (id, user_id, keyword, created_at) VALUES
(1001, 2,    '麻辣烫',   NOW() - INTERVAL 2 DAY),
(1002, 2,    '奶茶',     NOW() - INTERVAL 3 HOUR),
(1003, 1001, '寿司',     NOW() - INTERVAL 1 DAY),
(1004, 1001, '水煮牛肉', NOW() - INTERVAL 4 DAY),
(1005, 1002, '炸鸡',     NOW() - INTERVAL 6 HOUR)
ON DUPLICATE KEY UPDATE keyword = VALUES(keyword), created_at = VALUES(created_at);

INSERT INTO shopping_cart (id, user_id, merchant_id, dish_id, dish_name, image, price, quantity, created_at) VALUES
(1001, 2,    1002, 1008, '生椰拿铁',  '/uploads/demo/dish-1008.jpg', 18.00, 1, NOW() - INTERVAL 2 HOUR),
(1002, 1001, 1003, 1014, '豪华寿司拼盘', '/uploads/demo/dish-1014.jpg', 88.00, 1, NOW() - INTERVAL 1 HOUR)
ON DUPLICATE KEY UPDATE quantity = VALUES(quantity), price = VALUES(price);

-- ============================================================
-- 11. 站内通知（type 取值与 NotificationService 调用一致）
-- ============================================================
INSERT INTO notification (id, user_id, type, title, content, is_read, created_at) VALUES
(1001, 2,    'ORDER_STATUS', '订单已送达',   '订单 DM202609290001 已送达，快去评价吧',        0, NOW() - INTERVAL 6 DAY + INTERVAL 36 MINUTE),
(1002, 2,    'IM',           '商家回复了你', '川味小馆：谢谢支持，欢迎常来~',                 1, NOW() - INTERVAL 6 DAY + INTERVAL 1 HOUR),
(1003, 2,    'ORDER_STATUS', '骑手已接单',   '订单 DM202610050011 骑手已接单，正在赶来',      1, NOW() - INTERVAL 3 HOUR + INTERVAL 17 MINUTE),
(1004, 1001, 'ORDER_STATUS', '订单已送达',   '订单 DM202610050012 已送达，感谢下单',          0, NOW() - INTERVAL 2 HOUR + INTERVAL 27 MINUTE),
(1005, 1001, 'TICKET',       '售后工单有新的处理进展', '工单 #1001（退款）状态：待处理',        0, NOW() - INTERVAL 30 MINUTE),
(1006, 1003, 'ORDER_STATUS', '新订单',       '你有新的订单 DM202610050005，请尽快接单',       0, NOW() - INTERVAL 20 MINUTE)
ON DUPLICATE KEY UPDATE title = VALUES(title), content = VALUES(content),
  is_read = VALUES(is_read), created_at = VALUES(created_at);

-- ============================================================
-- 12. 订单会话（用户 ↔ 商家）+ 售后工单
-- ============================================================
INSERT INTO im_session (id, order_id, user_id, merchant_id, status, last_msg, last_msg_at,
                        user_unread, merchant_unread, created_at) VALUES
(1001, 1001, 2,    1,    'OPEN', '[工单] 退款 ¥6.00',   NOW() - INTERVAL 30 MINUTE, 1, 0, NOW() - INTERVAL 6 DAY),
(1002, 1004, 1001, 1003, 'OPEN', '老板，芥末多给两包可以吗', NOW() - INTERVAL 40 MINUTE, 0, 1, NOW() - INTERVAL 44 MINUTE)
ON DUPLICATE KEY UPDATE last_msg = VALUES(last_msg), last_msg_at = VALUES(last_msg_at),
  user_unread = VALUES(user_unread), merchant_unread = VALUES(merchant_unread), status = VALUES(status);

INSERT INTO im_message (id, session_id, sender_role, msg_type, content, payload, created_at) VALUES
(1001, 1002, 'USER',     'TEXT', '老板，芥末多给两包可以吗', NULL, NOW() - INTERVAL 44 MINUTE),
(1002, 1002, 'MERCHANT', 'TEXT', '可以的，已经备注给后厨了~', NULL, NOW() - INTERVAL 40 MINUTE),
(1003, 1001, 'USER',     'TEXT', '红烧肉分量好像比上次少了一点', NULL, NOW() - INTERVAL 45 MINUTE),
(1004, 1001, 'MERCHANT', 'TEXT', '实在抱歉，可以给你补一张 6 元券', NULL, NOW() - INTERVAL 35 MINUTE),
(1005, 1001, 'USER',     'TICKET', '[工单] 退款 ¥6.00',
 '{"ticketId":1001,"type":"REFUND","typeText":"退款","amount":6.00,"reason":"红烧肉分量偏少，申请部分退款","images":[],"status":"PENDING","statusText":"待处理","merchantReply":null}',
 NOW() - INTERVAL 34 MINUTE),
(1006, 1001, 'SYSTEM',   'ORDER', '[订单]',
 '{"orderId":1001,"orderNo":"DM202609290001","status":"DELIVERED","payAmount":46.30,"createdAt":"2026-09-29 20:15:00","merchantId":1,"merchantName":"张记小厨","merchantLogo":"/uploads/demo/shop-1-logo.jpg","items":[{"dishName":"招牌红烧肉饭","quantity":1,"price":28.00,"image":"/uploads/demo/dish-1.jpg"},{"dishName":"宫保鸡丁饭","quantity":1,"price":22.00,"image":"/uploads/demo/dish-2.jpg"}]}',
 NOW() - INTERVAL 6 DAY + INTERVAL 36 MINUTE)
ON DUPLICATE KEY UPDATE content = VALUES(content), payload = VALUES(payload);

INSERT INTO im_ticket (id, session_id, order_id, user_id, merchant_id, type, amount, reason, images,
                       status, merchant_reply, created_at, updated_at) VALUES
(1001, 1001, 1001, 2, 1, 'REFUND', 6.00, '红烧肉分量偏少，申请部分退款', '[]',
 'PENDING', NULL, NOW() - INTERVAL 34 MINUTE, NOW() - INTERVAL 34 MINUTE)
ON DUPLICATE KEY UPDATE reason = VALUES(reason), amount = VALUES(amount), status = VALUES(status);

-- ============================================================
-- 13. AI 客服会话（role 取 user/assistant，与 AiService 落库一致）
-- ============================================================
INSERT INTO chat_session (id, user_id, title, created_at) VALUES
(1001, 2,    '推荐点清淡的', NOW() - INTERVAL 5 HOUR),
(1002, 1001, '订单多久能到', NOW() - INTERVAL 2 HOUR)
ON DUPLICATE KEY UPDATE title = VALUES(title);

INSERT INTO chat_message (id, session_id, role, content, created_at) VALUES
(1001, 1001, 'user',      '我最近想吃点清淡的，有什么推荐吗？',                       NOW() - INTERVAL 5 HOUR),
(1002, 1001, 'assistant', '可以试试川味小馆的「麻婆豆腐」和「红糖冰粉」，微辣不油腻；深海寿司的「日式茶碗蒸」也很清爽。', NOW() - INTERVAL 5 HOUR + INTERVAL 5 SECOND),
(1003, 1001, 'user',      '冰粉多少钱？',                                             NOW() - INTERVAL 5 HOUR + INTERVAL 20 SECOND),
(1004, 1001, 'assistant', '川味小馆的红糖冰粉 8 元一份，满 20 元起送，目前还有「新人无门槛立减券」可用。', NOW() - INTERVAL 5 HOUR + INTERVAL 25 SECOND),
(1005, 1002, 'user',      '我的订单大概还要多久送到？',                               NOW() - INTERVAL 2 HOUR),
(1006, 1002, 'assistant', '订单 DM202610050012 已送达（骑手 26 分钟完成配送）。如需查看历史订单，可在「我的订单」里筛选状态。', NOW() - INTERVAL 2 HOUR + INTERVAL 5 SECOND)
ON DUPLICATE KEY UPDATE content = VALUES(content);

-- ============================================================
-- 14. 用户行为流水 + 推荐曝光日志（供推荐/画像类接口演示）
-- ============================================================
INSERT INTO user_behavior (id, user_id, merchant_id, dish_id, action, created_at) VALUES
(1001, 2,    1002, 1007, 'VIEW',   NOW() - INTERVAL 3 HOUR),
(1002, 2,    1002, 1007, 'CART',   NOW() - INTERVAL 3 HOUR + INTERVAL 1 MINUTE),
(1003, 2,    1002, 1007, 'ORDER',  NOW() - INTERVAL 3 DAY),
(1004, 2,    1002, 1012, 'ORDER',  NOW() - INTERVAL 3 DAY),
(1005, 2,    1,    1,    'ORDER',  NOW() - INTERVAL 6 DAY),
(1006, 2,    1,    1,    'REVIEW', NOW() - INTERVAL 6 DAY + INTERVAL 50 MINUTE),
(1007, 2,    1002, 1012, 'FAV',    NOW() - INTERVAL 3 DAY),
(1008, 2,    1003, 1014, 'VIEW',   NOW() - INTERVAL 7 MINUTE),
(1009, 1001, 1001, 1001, 'VIEW',   NOW() - INTERVAL 4 DAY),
(1010, 1001, 1001, 1001, 'ORDER',  NOW() - INTERVAL 4 DAY),
(1011, 1001, 1001, 1006, 'ORDER',  NOW() - INTERVAL 4 DAY),
(1012, 1001, 1003, 1013, 'VIEW',   NOW() - INTERVAL 45 MINUTE),
(1013, 1001, 1003, 1013, 'ORDER',  NOW() - INTERVAL 45 MINUTE),
(1014, 1001, 1003, 1018, 'VIEW',   NOW() - INTERVAL 50 MINUTE),
(1015, 1001, 1002, 1008, 'CART',   NOW() - INTERVAL 2 HOUR),
(1016, 1001, 1001, 1002, 'ORDER',  NOW() - INTERVAL 2 HOUR)
ON DUPLICATE KEY UPDATE action = VALUES(action), created_at = VALUES(created_at);

INSERT INTO recommend_log (id, user_id, dish_id, merchant_id, source, score, is_exposed, is_clicked, is_ordered, reason, created_at) VALUES
(1001, 2,    1007, 1002, 'HOT',    0.9120, 1, 1, 1, '近 30 天同类销量第一',       NOW() - INTERVAL 3 HOUR),
(1002, 2,    1012, 1002, 'ITEMCF', 0.8735, 1, 1, 0, '和你买过的芋泥啵啵奶茶相似', NOW() - INTERVAL 3 HOUR),
(1003, 2,    1001, 1001, 'ITEMCF', 0.8420, 1, 0, 0, '你点过水煮牛肉，口味相近',   NOW() - INTERVAL 2 HOUR),
(1004, 2,    1014, 1003, 'GEO',    0.8010, 1, 1, 0, '配送范围内高评分菜品',       NOW() - INTERVAL 7 MINUTE),
(1005, 1001, 1013, 1003, 'HOT',    0.9300, 1, 1, 1, '近期热门，很多人都在点',     NOW() - INTERVAL 45 MINUTE),
(1006, 1001, 1008, 1002, 'TAG',    0.7650, 1, 0, 0, '命中你常点的「咖啡」标签',   NOW() - INTERVAL 2 HOUR)
ON DUPLICATE KEY UPDATE score = VALUES(score), source = VALUES(source), reason = VALUES(reason);

-- ============================================================
-- 15. 菜品日销量（近 30 天 × 全部在售菜品，供 AI 销量预测/经营分析）
--     用 SIN 制造周内波动，保证预测曲线不是一条直线
-- ============================================================
INSERT INTO dish_sales_daily (merchant_id, dish_id, stat_date, quantity, amount)
SELECT s.merchant_id, s.dish_id, s.stat_date, s.qty, ROUND(s.qty * d.price, 2)
FROM (
    SELECT d.merchant_id AS merchant_id,
           d.id          AS dish_id,
           DATE_SUB(CURDATE(), INTERVAL n.n DAY) AS stat_date,
           CAST(ROUND(GREATEST(1,
                (8 + (d.id % 6) * 5) * (1 + 0.35 * SIN((n.n + d.id) / 2.2))
           )) AS SIGNED) AS qty
    FROM dish d
    JOIN (
        SELECT 0 AS n UNION ALL SELECT 1  UNION ALL SELECT 2  UNION ALL SELECT 3  UNION ALL SELECT 4
        UNION ALL SELECT 5  UNION ALL SELECT 6  UNION ALL SELECT 7  UNION ALL SELECT 8  UNION ALL SELECT 9
        UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14
        UNION ALL SELECT 15 UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19
        UNION ALL SELECT 20 UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23 UNION ALL SELECT 24
        UNION ALL SELECT 25 UNION ALL SELECT 26 UNION ALL SELECT 27 UNION ALL SELECT 28 UNION ALL SELECT 29
    ) n
    WHERE d.status = 1
      AND d.merchant_id IN (1, 1001, 1002, 1003)
) s
JOIN dish d ON d.id = s.dish_id
ON DUPLICATE KEY UPDATE quantity = VALUES(quantity), amount = VALUES(amount);

-- ============================================================
-- 16. 竞价投放 + 曝光扣费日志
-- ============================================================
INSERT INTO bid_campaign (id, merchant_id, keyword, bid, daily_budget, today_spent, status, start_date, end_date, created_at) VALUES
(1001, 1001, '川菜',   1.50, 100.00, 23.50, 1, CURDATE() - INTERVAL 10 DAY, CURDATE() + INTERVAL 20 DAY, NOW() - INTERVAL 10 DAY),
(1002, 1002, '奶茶',   2.20, 150.00, 61.80, 1, CURDATE() - INTERVAL 7 DAY,  CURDATE() + INTERVAL 23 DAY, NOW() - INTERVAL 7 DAY),
(1003, 1003, '寿司',   3.00, 80.00,  0.00,  0, CURDATE() + INTERVAL 1 DAY,  CURDATE() + INTERVAL 31 DAY, NOW() - INTERVAL 1 DAY)
ON DUPLICATE KEY UPDATE keyword = VALUES(keyword), bid = VALUES(bid),
  daily_budget = VALUES(daily_budget), today_spent = VALUES(today_spent), status = VALUES(status);

INSERT INTO bid_log (id, campaign_id, user_id, position, clicked, cost, created_at) VALUES
(1001, 1001, 2,    1, 1, 1.50, NOW() - INTERVAL 3 HOUR),
(1002, 1001, 1001, 3, 0, 0.00, NOW() - INTERVAL 2 HOUR),
(1003, 1002, 2,    1, 1, 2.20, NOW() - INTERVAL 3 HOUR + INTERVAL 5 MINUTE),
(1004, 1002, NULL, 2, 0, 0.00, NOW() - INTERVAL 90 MINUTE),
(1005, 1002, 1001, 1, 1, 2.20, NOW() - INTERVAL 2 HOUR + INTERVAL 10 MINUTE),
(1006, 1001, NULL, 5, 0, 0.00, NOW() - INTERVAL 40 MINUTE)
ON DUPLICATE KEY UPDATE clicked = VALUES(clicked), cost = VALUES(cost);

-- ============================================================
-- 17. 骑手轨迹（配送中订单 1004 的实时路径，供地图轨迹页演示）
-- ============================================================
INSERT INTO rider_location (id, rider_id, order_id, lng, lat, created_at) VALUES
(1001, 1002, 1004, 116.405500, 39.921000, NOW() - INTERVAL 20 MINUTE),
(1002, 1002, 1004, 116.402100, 39.919500, NOW() - INTERVAL 15 MINUTE),
(1003, 1002, 1004, 116.399000, 39.918000, NOW() - INTERVAL 10 MINUTE),
(1004, 1002, 1004, 116.396500, 39.916800, NOW() - INTERVAL 5 MINUTE),
(1005, 1002, 1004, 116.394800, 39.915900, NOW() - INTERVAL 1 MINUTE)
ON DUPLICATE KEY UPDATE lng = VALUES(lng), lat = VALUES(lat), created_at = VALUES(created_at);

-- ============================================================
-- 18. 管理端操作日志（演示「操作日志」页的筛选）
-- ============================================================
INSERT INTO operation_log (id, admin_id, action, target_type, target_id, detail, ip, created_at) VALUES
(1001, 1, 'AUDIT_MERCHANT', 'MERCHANT', 1001, '审核通过：川味小馆',           '127.0.0.1', NOW() - INTERVAL 59 DAY),
(1002, 1, 'AUDIT_MERCHANT', 'MERCHANT', 1002, '审核通过：甜芯奶茶',           '127.0.0.1', NOW() - INTERVAL 54 DAY),
(1003, 1, 'AUDIT_RIDER',    'RIDER',    1001, '审核通过：王快跑',             '127.0.0.1', NOW() - INTERVAL 34 DAY),
(1004, 1, 'CHANGE_USER_STATUS', 'USER', 1009, '账号已启用',                   '127.0.0.1', NOW() - INTERVAL 1 DAY),
(1005, 1, 'REFUND_ORDER',   'ORDER',    1010, '同意退款：奶茶机故障 ¥45.15',  '127.0.0.1', NOW() - INTERVAL 1 DAY + INTERVAL 6 MINUTE)
ON DUPLICATE KEY UPDATE detail = VALUES(detail), created_at = VALUES(created_at);

-- ============================================================
-- 完成。数据核对（应分别输出 9 / 5 / 12 / 6 / 5 / 5）：
--   SELECT COUNT(*) FROM user WHERE id >= 1000;          -- 新增账号
--   SELECT COUNT(*) FROM merchant WHERE id >= 1000;      -- 新增商家
--   SELECT COUNT(*) FROM orders WHERE id >= 1000;        -- 示例订单
--   SELECT COUNT(*) FROM review WHERE id >= 1000;        -- 示例评价
--   SELECT COUNT(*) FROM im_session WHERE id >= 1000;    -- 示例会话
--   SELECT COUNT(*) FROM im_ticket WHERE id >= 1000;     -- 示例工单
--   SELECT status, COUNT(*) FROM orders WHERE id >= 1000 GROUP BY status;
--   -- 分账自检：应输出 0 行（商家实收+骑手实收+平台收入 == 菜品金额+打包费+配送费-优惠）
--   SELECT id, pay_amount,
--          (merchant_income + rider_income + platform_income) AS split_sum,
--          (dish_amount + package_fee + delivery_fee - discount_amount) AS expected
--     FROM orders WHERE id >= 1000
--    HAVING ABS(split_sum - expected) > 0.001;
-- ============================================================

-- ============================================================
-- 一键清理（需要回到干净环境时执行；仅删除 1000+ 的演示行，
-- DataInitializer 建的 1~999 行与 1 号商家菜品图片的 UPDATE 不在范围内）
-- ============================================================
-- DELETE FROM operation_log WHERE id >= 1000;
-- DELETE FROM rider_location WHERE id >= 1000;
-- DELETE FROM bid_log WHERE id >= 1000;
-- DELETE FROM bid_campaign WHERE id >= 1000;
-- DELETE FROM dish_sales_daily WHERE dish_id >= 1000;
-- DELETE FROM recommend_log WHERE id >= 1000;
-- DELETE FROM user_behavior WHERE id >= 1000;
-- DELETE FROM chat_message WHERE id >= 1000;
-- DELETE FROM chat_session WHERE id >= 1000;
-- DELETE FROM im_ticket WHERE id >= 1000;
-- DELETE FROM im_message WHERE id >= 1000;
-- DELETE FROM im_session WHERE id >= 1000;
-- DELETE FROM notification WHERE id >= 1000;
-- DELETE FROM shopping_cart WHERE id >= 1000;
-- DELETE FROM search_history WHERE id >= 1000;
-- DELETE FROM favorite WHERE id >= 1000;
-- DELETE FROM review WHERE id >= 1000;
-- DELETE FROM order_status_log WHERE id >= 1000;
-- DELETE FROM order_item WHERE id >= 1000;
-- DELETE FROM orders WHERE id >= 1000;
-- DELETE FROM user_coupon WHERE id >= 1000;
-- DELETE FROM coupon WHERE id >= 1000;
-- DELETE FROM address WHERE id >= 1000;
-- DELETE FROM rider WHERE id >= 1000;
-- DELETE FROM dish WHERE id >= 1000;
-- DELETE FROM dish_category WHERE id >= 1000;
-- DELETE FROM merchant WHERE id >= 1000;
-- DELETE FROM `user` WHERE id >= 1000;
-- -- 还原 1 号商家被补的图片（如需完全回到初始状态）：
-- UPDATE dish SET image = NULL WHERE id BETWEEN 1 AND 6;
-- UPDATE merchant SET logo = NULL, cover = NULL WHERE id = 1;

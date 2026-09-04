-- 选择项目数据库。
USE flash_sale;

-- 添加更多演示商品，方便测试商品列表、详情、缓存和秒杀库存。
-- 每条 INSERT 都使用 WHERE NOT EXISTS，脚本重复执行时不会产生重复商品。

INSERT INTO app_product (
    name, description, price, seckill_price, stock, seckill_stock,
    status, seckill_start_at, seckill_end_at
)
SELECT
    '无线蓝牙鼠标', '轻量静音无线鼠标，适合办公和学习', 129.00, 79.00,
    200, 80, 1,
    '2026-01-01 00:00:00', '2030-01-01 00:00:00'
WHERE NOT EXISTS (SELECT 1 FROM app_product WHERE name = '无线蓝牙鼠标');

INSERT INTO app_product (
    name, description, price, seckill_price, stock, seckill_stock,
    status, seckill_start_at, seckill_end_at
)
SELECT
    '主动降噪耳机', '支持主动降噪和蓝牙连接的头戴式耳机', 599.00, 399.00,
    120, 50, 1,
    '2026-01-01 00:00:00', '2030-01-01 00:00:00'
WHERE NOT EXISTS (SELECT 1 FROM app_product WHERE name = '主动降噪耳机');

INSERT INTO app_product (
    name, description, price, seckill_price, stock, seckill_stock,
    status, seckill_start_at, seckill_end_at
)
SELECT
    '27 英寸电竞显示器', '2K 分辨率、高刷新率电竞显示器', 1899.00, 1499.00,
    60, 20, 1,
    '2026-01-01 00:00:00', '2030-01-01 00:00:00'
WHERE NOT EXISTS (SELECT 1 FROM app_product WHERE name = '27 英寸电竞显示器');

INSERT INTO app_product (
    name, description, price, seckill_price, stock, seckill_stock,
    status, seckill_start_at, seckill_end_at
)
SELECT
    'USB-C 多功能扩展坞', '支持 HDMI、USB 和网线接口的扩展坞', 299.00, 199.00,
    150, 60, 1,
    '2026-01-01 00:00:00', '2030-01-01 00:00:00'
WHERE NOT EXISTS (SELECT 1 FROM app_product WHERE name = 'USB-C 多功能扩展坞');

INSERT INTO app_product (
    name, description, price, seckill_price, stock, seckill_stock,
    status, seckill_start_at, seckill_end_at
)
SELECT
    '铝合金手机支架', '可折叠桌面手机支架，支持多角度调节', 89.00, 49.00,
    300, 120, 1,
    '2026-01-01 00:00:00', '2030-01-01 00:00:00'
WHERE NOT EXISTS (SELECT 1 FROM app_product WHERE name = '铝合金手机支架');

INSERT INTO app_product (
    name, description, price, seckill_price, stock, seckill_stock,
    status, seckill_start_at, seckill_end_at
)
SELECT
    '便携式固态硬盘 1TB', '高速 USB-C 接口移动固态硬盘', 699.00, 499.00,
    100, 40, 1,
    '2026-01-01 00:00:00', '2030-01-01 00:00:00'
WHERE NOT EXISTS (SELECT 1 FROM app_product WHERE name = '便携式固态硬盘 1TB');

INSERT INTO app_product (
    name, description, price, seckill_price, stock, seckill_stock,
    status, seckill_start_at, seckill_end_at
)
SELECT
    '千兆路由器', '双频千兆无线路由器，适合家庭网络', 399.00, 259.00,
    180, 70, 1,
    '2026-09-01 00:00:00', '2026-12-31 23:59:59'
WHERE NOT EXISTS (SELECT 1 FROM app_product WHERE name = '千兆路由器');

INSERT INTO app_product (
    name, description, price, seckill_price, stock, seckill_stock,
    status, seckill_start_at, seckill_end_at
)
SELECT
    '机械键盘键帽套装', '适配常见机械键盘的彩色键帽套装', 159.00, 99.00,
    250, 100, 1,
    '2026-10-01 00:00:00', '2026-12-31 23:59:59'
WHERE NOT EXISTS (SELECT 1 FROM app_product WHERE name = '机械键盘键帽套装');

INSERT INTO app_product (
    name, description, price, seckill_price, stock, seckill_stock,
    status, seckill_start_at, seckill_end_at
)
SELECT
    '桌面 USB 麦克风', '适合在线会议和直播的桌面麦克风', 459.00, 299.00,
    90, 30, 1,
    '2025-01-01 00:00:00', '2026-01-01 00:00:00'
WHERE NOT EXISTS (SELECT 1 FROM app_product WHERE name = '桌面 USB 麦克风');

INSERT INTO app_product (
    name, description, price, seckill_price, stock, seckill_stock,
    status, seckill_start_at, seckill_end_at
)
SELECT
    '智能手环', '支持运动记录和睡眠监测的智能手环', 299.00, 169.00,
    220, 90, 0,
    '2026-01-01 00:00:00', '2030-01-01 00:00:00'
WHERE NOT EXISTS (SELECT 1 FROM app_product WHERE name = '智能手环');

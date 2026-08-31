-- 选择项目数据库。
USE flash_sale;

-- 插入一条演示商品，供商品查询接口和前端页面使用。
-- WHERE NOT EXISTS 保证重复执行脚本时不会重复插入。
INSERT INTO app_product (
    name, description, price, seckill_price, stock, seckill_stock,
    status, seckill_start_at, seckill_end_at
)
SELECT
    '演示机械键盘', '用于学习秒杀流程的演示商品', 299.00, 199.00,
    100, 100, 1,
    '2026-01-01 00:00:00', '2030-01-01 00:00:00'
WHERE NOT EXISTS (
    SELECT 1 FROM app_product WHERE name = '演示机械键盘'
);

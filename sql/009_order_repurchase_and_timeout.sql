USE flash_sale;

-- 已有数据库执行此迁移；不会删除订单或更改订单金额和状态。
-- 新库的 007 已包含这些字段。本脚本检查字段后再执行，可重复运行。
SET @order_upgrade_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'app_order'
             AND column_name = 'active_purchase'),
    'SELECT ''order schema already upgraded'' AS migration_status',
    'ALTER TABLE app_order
        DROP INDEX uk_app_order_user_product,
        ADD COLUMN active_purchase TINYINT GENERATED ALWAYS AS
            (CASE WHEN status IN (''PENDING_PAYMENT'', ''PAID'') THEN 1 ELSE NULL END) STORED,
        ADD COLUMN redis_stock_released TINYINT NOT NULL DEFAULT 0,
        ADD UNIQUE INDEX uk_app_order_active_purchase (user_id, product_id, active_purchase),
        ADD INDEX idx_app_order_timeout (status, created_at, id),
        ADD INDEX idx_app_order_release (status, redis_stock_released, id)'
);
PREPARE order_upgrade FROM @order_upgrade_sql;
EXECUTE order_upgrade;
DEALLOCATE PREPARE order_upgrade;

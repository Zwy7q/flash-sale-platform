USE flash_sale;

-- 一行代表一个用户购买一件秒杀商品，当前阶段数量固定为 1。
CREATE TABLE IF NOT EXISTS app_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '订单主键',
    request_id VARCHAR(64) NOT NULL COMMENT '秒杀请求编号，防止同一消息重复创建订单',
    user_id BIGINT NOT NULL COMMENT '购买用户 ID',
    product_id BIGINT NOT NULL COMMENT '秒杀商品 ID',
    amount DECIMAL(10, 2) NOT NULL COMMENT '下单时成交金额快照',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_PAYMENT'
        COMMENT 'PENDING_PAYMENT、PAID、CANCELLED、PAYMENT_TIMEOUT',
    -- MySQL 自动计算：有效订单为 1，关闭订单为 NULL，不需要 Java 手工维护。
    active_purchase TINYINT GENERATED ALWAYS AS
        (CASE WHEN status IN ('PENDING_PAYMENT', 'PAID') THEN 1 ELSE NULL END) STORED,
    redis_stock_released TINYINT NOT NULL DEFAULT 0 COMMENT 'Redis 库存与购买资格已释放：0否，1是',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT uk_app_order_request UNIQUE (request_id),
    -- UNIQUE 允许多个 NULL，因此可以保留多笔取消/超时历史，但只能有一笔有效订单。
    CONSTRAINT uk_app_order_active_purchase UNIQUE (user_id, product_id, active_purchase),
    INDEX idx_app_order_user_created (user_id, created_at),
    INDEX idx_app_order_timeout (status, created_at, id),
    INDEX idx_app_order_release (status, redis_stock_released, id),
    CONSTRAINT ck_app_order_amount CHECK (amount >= 0),
    CONSTRAINT ck_app_order_status CHECK
        (status IN ('PENDING_PAYMENT', 'PAID', 'CANCELLED', 'PAYMENT_TIMEOUT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀订单表';

-- 当前沿用项目做法，不声明物理外键；用户、商品有效性由业务层校验。

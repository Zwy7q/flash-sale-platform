-- 当前阶段只创建数据库连接验证需要的最小表结构。
-- 用户表和商品表的字段会在后续阶段逐步补充。
CREATE TABLE IF NOT EXISTS database_probe (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 选择项目数据库。
USE flash_sale;

-- 商品表保存商品基本信息和秒杀库存。
-- seckill_stock 是秒杀活动库存，和普通 stock 分开，便于后续扩展。
CREATE TABLE IF NOT EXISTS app_product (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '商品主键',
    name VARCHAR(128) NOT NULL COMMENT '商品名称',
    description VARCHAR(512) NULL COMMENT '商品描述',
    price DECIMAL(10, 2) NOT NULL COMMENT '商品原价',
    seckill_price DECIMAL(10, 2) NOT NULL COMMENT '秒杀价',
    stock INT NOT NULL DEFAULT 0 COMMENT '普通库存',
    seckill_stock INT NOT NULL DEFAULT 0 COMMENT '秒杀库存',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1上架，0下架',
    seckill_start_at DATETIME NOT NULL COMMENT '秒杀开始时间',
    seckill_end_at DATETIME NOT NULL COMMENT '秒杀结束时间',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COMMENT='秒杀商品表';

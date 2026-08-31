package com.example.flashsale.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 商品表结构测试。
 *
 * 当前阶段只验证 app_product 表是否存在，暂时不实现商品增删改查。
 */
@SpringBootTest
class ProductTableTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appProductTableShouldExist() {
        Integer tableCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'flash_sale'
                  AND table_name = 'app_product'
                """,
                Integer.class
        );

        assertEquals(1, tableCount);
    }
}

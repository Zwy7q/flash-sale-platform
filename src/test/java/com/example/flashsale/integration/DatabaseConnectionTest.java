package com.example.flashsale.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 数据库连接集成测试。
 *
 * 这个测试会启动完整的 Spring 容器，并通过 JdbcTemplate 执行真实 SQL。
 * 它和只测试 Controller 的 MockMvc 测试不同，需要本机 MySQL 正常运行。
 */
@SpringBootTest
class DatabaseConnectionTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldConnectToFlashSaleDatabase() {
        // database_probe 是初始化脚本创建的探针表，用来验证当前连接的是目标数据库。
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables " +
                        "WHERE table_schema = 'flash_sale' AND table_name = 'database_probe'",
                Integer.class
        );

        assertEquals(1, tableCount);
    }
    @Test
    void appUserTableShouldExist() {
        // 查询 information_schema，可以检查当前数据库中的表是否存在。
        Integer tableCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'flash_sale'
                  AND table_name = 'app_user'
                """,
                Integer.class
        );

        assertEquals(1, tableCount);
    }
}

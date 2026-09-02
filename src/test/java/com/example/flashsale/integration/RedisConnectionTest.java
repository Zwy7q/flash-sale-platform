package com.example.flashsale.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Redis 基础连接测试。
 *
 * 这个测试验证 Spring Boot 能否连接 Redis，并完成一次写入和读取。
 */
@SpringBootTest
class RedisConnectionTest {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void shouldWriteAndReadValue() {
        String key = "flash-sale:test:connection";
        String value = "connected";

        // set 写入字符串，并设置 60 秒过期时间，避免测试数据长期留在 Redis 中。
        stringRedisTemplate.opsForValue().set(key, value, Duration.ofSeconds(60));

        // get 读取刚才写入的值。
        String actualValue = stringRedisTemplate.opsForValue().get(key);

        assertEquals(value, actualValue);
    }
}

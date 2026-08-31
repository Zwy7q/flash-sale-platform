package com.example.flashsale;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 秒杀平台的 Spring Boot 启动类。
 *
 * @SpringBootApplication 是三个注解的组合：
 * 1. @Configuration：当前类可以声明 Spring 配置；
 * 2. @EnableAutoConfiguration：根据依赖自动配置 Spring Boot；
 * 3. @ComponentScan：扫描当前包及子包中的 Controller、Service 等组件。
 */
@SpringBootApplication
public class FlashSaleApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlashSaleApplication.class, args);
    }
}

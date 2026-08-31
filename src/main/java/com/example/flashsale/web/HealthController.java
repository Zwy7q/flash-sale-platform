package com.example.flashsale.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供最基础的服务健康检查接口。
 *
 * 后续接入数据库、Redis 和消息队列后，可以逐步把健康检查扩展为
 * 检查各个基础设施的连接状态。
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    /**
     * 浏览器访问 GET /api/health 时返回 OK，表示 Web 服务已经启动。
     */
    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}

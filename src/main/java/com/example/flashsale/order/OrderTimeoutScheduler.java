package com.example.flashsale.order;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定时调度只负责触发，具体超时业务仍放在 Service 中。 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.order.timeout-enabled", havingValue = "true", matchIfMissing = true)
public class OrderTimeoutScheduler {
    private final OrderTimeoutService timeoutService;

    public OrderTimeoutScheduler(OrderTimeoutService timeoutService) {
        this.timeoutService = timeoutService;
    }

    /** fixedDelay 从上一次执行结束开始计时，单实例内不会重叠执行。 */
    @Scheduled(fixedDelayString = "${app.order.timeout-scan-interval-ms:60000}",
            initialDelayString = "${app.order.timeout-scan-interval-ms:60000}")
    public void scan() {
        timeoutService.closeExpiredOrders();
    }
}

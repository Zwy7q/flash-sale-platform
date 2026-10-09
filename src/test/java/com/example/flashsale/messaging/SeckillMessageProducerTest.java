package com.example.flashsale.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真实 broker 测试覆盖路由；此处模拟很难稳定触发的超时、NACK 和线程中断。 */
class SeckillMessageProducerTest {
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final SeckillMessage task = new SeckillMessage("test-request", 1L, 1L, new BigDecimal("129.00"));

    private SeckillMessageProducer producer() {
        return new SeckillMessageProducer(rabbit, new ObjectMapper(), "test-queue", 10);
    }

    @Test
    void shouldReturnOnlyAfterPositiveConfirm() {
        doAnswer(call -> {
            CorrelationData data = call.getArgument(3);
            data.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).convertAndSend(eq(""), eq("test-queue"), any(Object.class), any(CorrelationData.class));
        assertDoesNotThrow(() -> producer().send(task));
    }

    @Test
    void shouldTreatMissingConfirmAsUnknown() {
        // 模板没有完成确认 Future，10ms 后超时；不能据此认为 broker 没收到消息。
        MessagePublishException error = assertThrows(MessagePublishException.class, () -> producer().send(task));
        assertEquals(MessagePublishException.Outcome.UNKNOWN, error.getOutcome());
    }

    @Test
    void shouldTreatNegativeConfirmConservativelyAsUnknown() {
        doAnswer(call -> {
            CorrelationData data = call.getArgument(3);
            data.getFuture().complete(new CorrelationData.Confirm(false, "broker nack"));
            return null;
        }).when(rabbit).convertAndSend(eq(""), eq("test-queue"), any(Object.class), any(CorrelationData.class));
        MessagePublishException error = assertThrows(MessagePublishException.class, () -> producer().send(task));
        assertEquals(MessagePublishException.Outcome.UNKNOWN, error.getOutcome());
    }

    @Test
    void shouldPreserveInterruptedStatusAndReportUnknown() {
        doAnswer(call -> {
            Thread.currentThread().interrupt();
            return null;
        }).when(rabbit).convertAndSend(eq(""), eq("test-queue"), any(Object.class), any(CorrelationData.class));
        try {
            MessagePublishException error = assertThrows(MessagePublishException.class, () -> producer().send(task));
            assertEquals(MessagePublishException.Outcome.UNKNOWN, error.getOutcome());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted(); // 清除测试线程的中断标志，避免影响其他测试。
        }
    }
}

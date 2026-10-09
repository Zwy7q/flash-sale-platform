package com.example.flashsale.messaging;

/** 发送结果分类，供后续业务层决定查询、重试或补偿；本异常不执行补偿。 */
public class MessagePublishException extends RuntimeException {
    public enum Outcome {
        NOT_ROUTED, // broker 明确退回：本次发布没有路由到队列。
        UNKNOWN     // 超时、断连、NACK 等：保守处理，不证明消息没被消费。
    }
    private final Outcome outcome;

    public MessagePublishException(Outcome outcome, String message, Throwable cause) {
        super(message, cause);
        this.outcome = outcome;
    }

    public Outcome getOutcome() {
        return outcome;
    }
}

package com.example.flashsale.messaging;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

/** 下单任务的数据，不是订单实体，也不是前端请求参数。 */
@Getter
@Setter
@NoArgsConstructor // Jackson 反序列化时先创建对象，再填充字段。
@AllArgsConstructor // 生产者构造任务时方便一次提供全部字段。
public class SeckillMessage {
    private String requestId; // 每次真实抢购任务唯一；重试/重复投递必须保持不变。
    private Long userId;
    private Long productId;
    private BigDecimal amount; // 资格获得时的成交金额快照，由可信后端提供。
}

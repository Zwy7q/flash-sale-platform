package com.example.flashsale.seckill;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 秒杀接口返回给客户端的响应对象。 */
@Getter
@AllArgsConstructor
public class SeckillResponse {

    /** 本次秒杀请求的业务结果。 */
    private SeckillResult result;

    /** 便于前端直接展示的中文提示。 */
    private String message;

    /** 本次下单任务编号；未获得资格时为 null，发送未知时也返回以便查询。 */
    private String requestId;

    public SeckillResponse(SeckillResult result, String message) {
        this(result, message, null);
    }
}

package com.example.flashsale.seckill;

/** 秒杀请求的最终业务结果。 */
public enum SeckillResult {

    /** 已获得秒杀资格，库存已在 Redis 中预扣。 */
    SUCCESS,

    /** 商品不存在或已下架。 */
    PRODUCT_NOT_FOUND,

    /** 秒杀活动尚未开始或已经结束。 */
    SECKILL_NOT_ACTIVE,

    /** 用户已经抢购过同一件商品。 */
    DUPLICATE_PURCHASE,

    /** 秒杀库存已售罄。 */
    OUT_OF_STOCK,

    /** Redis 中未能获得商品库存。 */
    STOCK_NOT_WARMED_UP,
    /** 本次发布被明确退回；当前基础版保留预扣供人工核对。 */
    PUBLISH_FAILED,
    /** 发送结果未知，不得立即恢复库存。 */
    PUBLISH_UNKNOWN,
    /** 查询尚未找到订单，可能排队、未发送或消费失败，不能断言具体原因。 */
    RESULT_UNKNOWN
}

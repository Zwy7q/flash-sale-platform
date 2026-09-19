package com.example.flashsale.seckill;

/**
 * 秒杀预扣库存的结果。
 *
 * 把不同失败原因明确区分，Controller 层后续才能返回合适的提示。
 */
public enum StockDeductionResult {

    /** 库存扣减成功，并记录了用户购买标记。 */
    SUCCESS,

    /** 当前用户已经购买过该商品。 */
    DUPLICATE_PURCHASE,

    /** Redis 中库存为 0。 */
    OUT_OF_STOCK,

    /** Redis 中还没有该商品的库存 Key。 */
    STOCK_NOT_WARMED_UP
}

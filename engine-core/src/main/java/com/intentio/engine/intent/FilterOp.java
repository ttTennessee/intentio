package com.intentio.engine.intent;

/**
 * 查询过滤操作符。EQ 为默认（兼容旧的等值过滤）。
 */
public enum FilterOp {
    EQ,
    NE,
    GT,
    GTE,
    LT,
    LTE,
    LIKE,
    CONTAINS,
    IN,
    IS_NULL,
    IS_NOT_NULL
}

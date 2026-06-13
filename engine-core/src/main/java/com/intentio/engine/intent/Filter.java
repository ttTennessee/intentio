package com.intentio.engine.intent;

/**
 * 单个查询过滤条件。{@code value} 对 IS_NULL / IS_NOT_NULL 无意义；
 * 对 IN 应为 {@link java.util.Collection} 或数组。
 */
public record Filter(String field, FilterOp op, Object value) {
}

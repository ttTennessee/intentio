package com.intentio.engine.intent;

/**
 * 单个排序项。{@code asc=true} 升序，false 降序。
 */
public record Order(String field, boolean asc) {
}

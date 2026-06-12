package com.intentio.engine.logging;

import org.slf4j.Logger;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * 统一输出 PreparedStatement 的 SQL 模板与绑定参数。
 */
public final class SqlLogger {

    private SqlLogger() {}

    /**
     * 在 INFO 级别记录 SQL 与参数（默认日志级别即可看见）。
     *
     * @param log    调用方 logger
     * @param label  操作标识，如 {@code INSERT rx@prescription}
     * @param sql    SQL 模板（含 {@code ?} 占位符）
     * @param params 按顺序绑定的参数
     */
    public static void sql(Logger log, String label, String sql, Object... params) {
        if (!log.isInfoEnabled()) return;
        log.info("{} sql={} params={}", label, sql, formatParams(params));
    }

    /**
     * 在 INFO 级别记录 SQL 与参数列表。
     *
     * @param log    调用方 logger
     * @param label  操作标识
     * @param sql    SQL 模板
     * @param params 按顺序绑定的参数列表
     */
    public static void sql(Logger log, String label, String sql, Collection<?> params) {
        if (!log.isInfoEnabled()) return;
        log.info("{} sql={} params={}", label, sql, params);
    }

    private static String formatParams(Object[] params) {
        if (params == null || params.length == 0) return "[]";
        if (params.length == 1 && params[0] instanceof Collection<?> c) {
            return c.toString();
        }
        return Arrays.stream(params)
            .map(v -> v == null ? "null" : v.toString())
            .collect(Collectors.joining(", ", "[", "]"));
    }
}

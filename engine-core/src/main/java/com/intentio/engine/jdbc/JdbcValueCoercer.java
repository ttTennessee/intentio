package com.intentio.engine.jdbc;

import com.intentio.engine.schema.FieldDef;
import com.intentio.engine.schema.FieldType;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;

/**
 * 将 JDBC {@link java.sql.ResultSet} 原始值按 schema 字段类型归一化为 Java 时间类型，
 * 与 {@link com.intentio.engine.execute.Executor} 写入路径保持一致。
 */
public final class JdbcValueCoercer {

    private static final DateTimeFormatter SQL_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private JdbcValueCoercer() {}

    public static Object coerce(Object value, FieldDef def) {
        if (value == null || def == null) return null;
        return switch (def.type()) {
            case DATE -> coerceDate(value);
            case DATETIME -> coerceDateTime(value);
            default -> value;
        };
    }

    public static Object coerce(Object value, FieldType type) {
        if (value == null || type == null) return null;
        return switch (type) {
            case DATE -> coerceDate(value);
            case DATETIME -> coerceDateTime(value);
            default -> value;
        };
    }

    static LocalDate coerceDate(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDate ld) return ld;
        if (value instanceof java.sql.Date d) return d.toLocalDate();
        if (value instanceof Timestamp ts) return ts.toLocalDateTime().toLocalDate();
        if (value instanceof Date d) {
            return new java.sql.Date(d.getTime()).toLocalDate();
        }
        String s = value.toString().trim();
        if (s.isEmpty()) return null;
        if (s.length() >= 10 && s.charAt(4) == '-' && s.charAt(7) == '-') {
            return LocalDate.parse(s.substring(0, 10));
        }
        return LocalDate.parse(s);
    }

    static LocalDateTime coerceDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime ldt) return ldt;
        if (value instanceof Timestamp ts) return ts.toLocalDateTime();
        if (value instanceof java.sql.Date d) return d.toLocalDate().atStartOfDay();
        if (value instanceof Date d) {
            return new Timestamp(d.getTime()).toLocalDateTime();
        }
        return parseDateTimeString(value.toString().trim());
    }

    private static LocalDateTime parseDateTimeString(String s) {
        if (s.isEmpty()) return null;
        if (s.length() == 10) return LocalDate.parse(s).atStartOfDay();
        try {
            return LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            return LocalDateTime.parse(s, SQL_DATETIME);
        }
    }
}

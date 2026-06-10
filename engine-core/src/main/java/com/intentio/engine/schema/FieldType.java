package com.intentio.engine.schema;

public enum FieldType {
    LONG,
    STRING,
    DECIMAL,
    DATE,
    DATETIME,
    BOOLEAN,
    ENUM;

    public static FieldType parse(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("field type is null");
        }
        return switch (raw.toLowerCase()) {
            case "long", "int", "integer", "bigint" -> LONG;
            case "string", "text", "varchar" -> STRING;
            case "decimal", "numeric" -> DECIMAL;
            case "date" -> DATE;
            case "datetime", "timestamp" -> DATETIME;
            case "boolean", "bool" -> BOOLEAN;
            case "enum" -> ENUM;
            default -> throw new IllegalArgumentException("unknown field type: " + raw);
        };
    }
}

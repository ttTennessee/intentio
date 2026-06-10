package com.intentio.engine.schema;

import java.util.Map;

public final class Rule {
    public enum Type {
        REQUIRE_HAS,
        STOCK_CHECK,
        REF_CHECK,
        EXPRESSION
    }

    private final Type type;
    private final Map<String, Object> params;
    private final String message;

    public Rule(Type type, Map<String, Object> params, String message) {
        this.type = type;
        this.params = params == null ? Map.of() : Map.copyOf(params);
        this.message = message;
    }

    public Type type() { return type; }
    public Map<String, Object> params() { return params; }
    public String message() { return message; }

    public Object param(String key) { return params.get(key); }
}

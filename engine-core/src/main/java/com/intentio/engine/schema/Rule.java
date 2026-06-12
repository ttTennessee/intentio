package com.intentio.engine.schema;

import java.util.Map;

public record Rule(Type type, Map<String, Object> params, String message) {

    public enum Type {
        REQUIRE_HAS,
        STOCK_CHECK,
        REF_CHECK,
        EXPRESSION
    }

    public Rule {
        params = params == null ? Map.of() : Map.copyOf(params);
    }

    public Object param(String key) {
        return params.get(key);
    }
}

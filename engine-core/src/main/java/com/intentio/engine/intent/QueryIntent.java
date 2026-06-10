package com.intentio.engine.intent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class QueryIntent {
    private final String entity;
    private final List<String> includes = new ArrayList<>();
    private final Map<String, Object> filters = new LinkedHashMap<>();
    private Integer limit;
    private Integer offset;

    private QueryIntent(String entity) {
        this.entity = entity;
    }

    public static QueryIntent from(String entity) {
        return new QueryIntent(entity);
    }

    public QueryIntent include(String... relations) {
        for (String r : relations) includes.add(r);
        return this;
    }

    public QueryIntent filter(String field, Object value) {
        filters.put(field, value);
        return this;
    }

    public QueryIntent limit(int limit) { this.limit = limit; return this; }
    public QueryIntent offset(int offset) { this.offset = offset; return this; }

    public String entity() { return entity; }
    public List<String> includes() { return includes; }
    public Map<String, Object> filters() { return filters; }
    public Integer limit() { return limit; }
    public Integer offset() { return offset; }
}

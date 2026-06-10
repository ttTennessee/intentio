package com.intentio.engine.result;

import java.util.List;
import java.util.Map;

public final class QueryResult {
    private final List<Map<String, Object>> rows;

    public QueryResult(List<Map<String, Object>> rows) {
        this.rows = rows == null ? List.of() : List.copyOf(rows);
    }

    public List<Map<String, Object>> rows() { return rows; }
    public int size() { return rows.size(); }
    public Map<String, Object> first() { return rows.isEmpty() ? null : rows.get(0); }
}

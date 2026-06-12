package com.intentio.engine.result;

import java.util.List;
import java.util.Map;

public record QueryResult(List<Map<String, Object>> rows) {

    public QueryResult {
        rows = rows == null ? List.of() : List.copyOf(rows);
    }

    public int size() {
        return rows.size();
    }

    public Map<String, Object> first() {
        return rows.isEmpty() ? null : rows.get(0);
    }
}

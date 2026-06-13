package com.intentio.engine.result;

import java.util.List;
import java.util.Map;

public record PageResult(List<Map<String, Object>> rows, long total) {

    public PageResult {
        rows = rows == null ? List.of() : List.copyOf(rows);
    }

    public int size() {
        return rows.size();
    }
}

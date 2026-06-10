package com.intentio.engine.vm;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PendingRow {
    private final String entity;
    private final Map<String, Object> fields;
    private Object generatedId;

    public PendingRow(String entity, Map<String, Object> fields) {
        this.entity = entity;
        this.fields = new LinkedHashMap<>(fields);
    }

    public String entity() { return entity; }
    public Map<String, Object> fields() { return fields; }
    public Object generatedId() { return generatedId; }

    public void setGeneratedId(Object id) {
        this.generatedId = id;
        fields.put("id", id);
    }

    public Object value(String field) {
        if ("id".equals(field) && generatedId != null) return generatedId;
        return fields.get(field);
    }
}

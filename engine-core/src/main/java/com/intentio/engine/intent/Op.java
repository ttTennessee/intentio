package com.intentio.engine.intent;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Op {
    private final OpType type;
    private final String entity;
    private final Map<String, Object> fields;
    private String name;
    private Object targetId;

    private Op(OpType type, String entity, Map<String, Object> fields) {
        this.type = type;
        this.entity = entity;
        this.fields = new LinkedHashMap<>(fields == null ? Map.of() : fields);
    }

    public static Op insert(String entity, Map<String, Object> fields) {
        return new Op(OpType.INSERT, entity, fields);
    }

    public static Op update(String entity, Object id, Map<String, Object> fields) {
        Op op = new Op(OpType.UPDATE, entity, fields);
        op.targetId = id;
        return op;
    }

    public static Op delete(String entity, Object id) {
        Op op = new Op(OpType.DELETE, entity, Map.of());
        op.targetId = id;
        return op;
    }

    public Op as(String name) {
        this.name = name;
        return this;
    }

    void assignDefaultName(int index) {
        if (this.name == null) {
            this.name = "op_" + index;
        }
    }

    public OpType type() { return type; }
    public String entity() { return entity; }
    public Map<String, Object> fields() { return fields; }
    public String name() { return name; }
    public Object targetId() { return targetId; }

    public void setField(String key, Object value) {
        fields.put(key, value);
    }
}

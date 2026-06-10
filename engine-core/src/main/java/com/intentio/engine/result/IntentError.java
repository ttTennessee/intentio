package com.intentio.engine.result;

public final class IntentError {
    private final String opId;
    private final String entity;
    private final String rule;
    private final String field;
    private final String message;

    public IntentError(String opId, String entity, String rule, String field, String message) {
        this.opId = opId;
        this.entity = entity;
        this.rule = rule;
        this.field = field;
        this.message = message;
    }

    public static IntentError of(String opId, String entity, String rule, String message) {
        return new IntentError(opId, entity, rule, null, message);
    }

    public static IntentError field(String opId, String entity, String field, String message) {
        return new IntentError(opId, entity, "field", field, message);
    }

    public String opId() { return opId; }
    public String entity() { return entity; }
    public String rule() { return rule; }
    public String field() { return field; }
    public String message() { return message; }

    @Override public String toString() {
        return "IntentError{op=" + opId + ", entity=" + entity + ", rule=" + rule
            + (field != null ? ", field=" + field : "") + ", msg=" + message + "}";
    }
}

package com.intentio.engine.result;

public record IntentError(String opId, String entity, String rule, String field, String message) {

    public static IntentError of(String opId, String entity, String rule, String message) {
        return new IntentError(opId, entity, rule, null, message);
    }

    public static IntentError field(String opId, String entity, String field, String message) {
        return new IntentError(opId, entity, "field", field, message);
    }

    @Override
    public String toString() {
        return "IntentError{op=" + opId + ", entity=" + entity + ", rule=" + rule
                + (field != null ? ", field=" + field : "") + ", msg=" + message + "}";
    }
}

package com.intentio.engine.result;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class IntentResult {
    private final boolean ok;
    private final Map<String, Object> generatedIds;
    private final List<IntentError> errors;

    private IntentResult(boolean ok, Map<String, Object> generatedIds, List<IntentError> errors) {
        this.ok = ok;
        this.generatedIds = generatedIds == null ? Map.of() : Map.copyOf(generatedIds);
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static IntentResult success(Map<String, Object> generatedIds) {
        return new IntentResult(true, generatedIds, List.of());
    }

    public static IntentResult failure(List<IntentError> errors) {
        return new IntentResult(false, new LinkedHashMap<>(), errors);
    }

    public boolean ok() { return ok; }
    public Map<String, Object> generatedIds() { return generatedIds; }
    public List<IntentError> errors() { return errors; }

    public Object generatedId(String opName) { return generatedIds.get(opName); }
}

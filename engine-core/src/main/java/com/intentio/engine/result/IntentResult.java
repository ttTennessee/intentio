package com.intentio.engine.result;

import java.util.List;
import java.util.Map;

public record IntentResult(boolean ok, Map<String, Object> generatedIds, List<IntentError> errors) {

    public IntentResult {
        generatedIds = generatedIds == null ? Map.of() : Map.copyOf(generatedIds);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static IntentResult success(Map<String, Object> generatedIds) {
        return new IntentResult(true, generatedIds, List.of());
    }

    public static IntentResult failure(List<IntentError> errors) {
        return new IntentResult(false, Map.of(), errors);
    }

    public Object generatedId(String opName) {
        return generatedIds.get(opName);
    }
}

package com.intentio.engine.schema;

import java.util.List;
import java.util.Map;

public record IntegrityDef(
        List<Rule> onCreate,
        List<Rule> onUpdate,
        List<Rule> onDelete,
        Map<String, Rule> fieldRules) {

    public IntegrityDef {
        onCreate = onCreate == null ? List.of() : List.copyOf(onCreate);
        onUpdate = onUpdate == null ? List.of() : List.copyOf(onUpdate);
        onDelete = onDelete == null ? List.of() : List.copyOf(onDelete);
        fieldRules = fieldRules == null ? Map.of() : Map.copyOf(fieldRules);
    }

    public static IntegrityDef empty() {
        return new IntegrityDef(List.of(), List.of(), List.of(), Map.of());
    }
}

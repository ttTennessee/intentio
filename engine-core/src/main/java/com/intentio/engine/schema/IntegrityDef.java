package com.intentio.engine.schema;

import java.util.List;
import java.util.Map;

public final class IntegrityDef {
    private final List<Rule> onCreate;
    private final List<Rule> onUpdate;
    private final List<Rule> onDelete;
    private final Map<String, Rule> fieldRules;

    public IntegrityDef(List<Rule> onCreate, List<Rule> onUpdate, List<Rule> onDelete, Map<String, Rule> fieldRules) {
        this.onCreate = onCreate == null ? List.of() : List.copyOf(onCreate);
        this.onUpdate = onUpdate == null ? List.of() : List.copyOf(onUpdate);
        this.onDelete = onDelete == null ? List.of() : List.copyOf(onDelete);
        this.fieldRules = fieldRules == null ? Map.of() : Map.copyOf(fieldRules);
    }

    public List<Rule> onCreate() { return onCreate; }
    public List<Rule> onUpdate() { return onUpdate; }
    public List<Rule> onDelete() { return onDelete; }
    public Map<String, Rule> fieldRules() { return fieldRules; }

    public static IntegrityDef empty() {
        return new IntegrityDef(List.of(), List.of(), List.of(), Map.of());
    }
}

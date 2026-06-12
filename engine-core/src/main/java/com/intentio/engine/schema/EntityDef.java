package com.intentio.engine.schema;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public record EntityDef(
        String name,
        String table,
        Map<String, FieldDef> fields,
        Map<String, RelationDef> relations,
        IntegrityDef integrity) {

    public EntityDef {
        fields = new LinkedHashMap<>(fields);
        relations = relations == null ? Map.of() : Map.copyOf(relations);
        integrity = integrity == null ? IntegrityDef.empty() : integrity;
    }

    public Optional<FieldDef> primaryKey() {
        return fields.values().stream().filter(FieldDef::pk).findFirst();
    }

    public Optional<RelationDef> relation(String name) {
        return Optional.ofNullable(relations.get(name));
    }
}

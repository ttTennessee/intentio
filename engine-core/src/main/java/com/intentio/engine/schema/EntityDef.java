package com.intentio.engine.schema;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class EntityDef {
    private final String name;
    private final String table;
    private final Map<String, FieldDef> fields;
    private final Map<String, RelationDef> relations;
    private final IntegrityDef integrity;

    public EntityDef(String name, String table,
                     Map<String, FieldDef> fields,
                     Map<String, RelationDef> relations,
                     IntegrityDef integrity) {
        this.name = name;
        this.table = table;
        this.fields = new LinkedHashMap<>(fields);
        this.relations = relations == null ? Map.of() : Map.copyOf(relations);
        this.integrity = integrity == null ? IntegrityDef.empty() : integrity;
    }

    public String name() { return name; }
    public String table() { return table; }
    public Map<String, FieldDef> fields() { return fields; }
    public Map<String, RelationDef> relations() { return relations; }
    public IntegrityDef integrity() { return integrity; }

    public Optional<FieldDef> primaryKey() {
        return fields.values().stream().filter(FieldDef::pk).findFirst();
    }

    public Optional<RelationDef> relation(String name) {
        return Optional.ofNullable(relations.get(name));
    }
}

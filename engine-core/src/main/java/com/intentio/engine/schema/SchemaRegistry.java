package com.intentio.engine.schema;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class SchemaRegistry {
    private final Map<String, EntityDef> entities;
    private final RelationGraph relationGraph;

    private SchemaRegistry(Map<String, EntityDef> entities) {
        this.entities = new LinkedHashMap<>(entities);
        this.relationGraph = RelationGraph.build(this);
    }

    public static SchemaRegistry of(Map<String, EntityDef> entities) {
        return new SchemaRegistry(entities);
    }

    public Optional<EntityDef> find(String name) {
        return Optional.ofNullable(entities.get(name));
    }

    public EntityDef require(String name) {
        EntityDef def = entities.get(name);
        if (def == null) {
            throw new IllegalArgumentException("Unknown entity: " + name);
        }
        return def;
    }

    public Collection<EntityDef> entities() {
        return entities.values();
    }

    public RelationGraph relationGraph() {
        return relationGraph;
    }
}

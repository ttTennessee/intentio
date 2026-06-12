package com.intentio.engine.schema;

public record RelationDef(String name, Kind kind, String targetEntity, String fk) {

    public enum Kind { BELONGS_TO, HAS_MANY, HAS_ONE }

    public static Kind parseKind(String raw) {
        return switch (raw.toLowerCase()) {
            case "belongs_to" -> Kind.BELONGS_TO;
            case "has_many" -> Kind.HAS_MANY;
            case "has_one" -> Kind.HAS_ONE;
            default -> throw new IllegalArgumentException("unknown relation kind: " + raw);
        };
    }
}

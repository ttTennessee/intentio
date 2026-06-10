package com.intentio.engine.schema;

public final class RelationDef {
    public enum Kind { BELONGS_TO, HAS_MANY, HAS_ONE }

    private final String name;
    private final Kind kind;
    private final String targetEntity;
    private final String fk;

    public RelationDef(String name, Kind kind, String targetEntity, String fk) {
        this.name = name;
        this.kind = kind;
        this.targetEntity = targetEntity;
        this.fk = fk;
    }

    public String name() { return name; }
    public Kind kind() { return kind; }
    public String targetEntity() { return targetEntity; }
    public String fk() { return fk; }

    public static Kind parseKind(String raw) {
        return switch (raw.toLowerCase()) {
            case "belongs_to" -> Kind.BELONGS_TO;
            case "has_many" -> Kind.HAS_MANY;
            case "has_one" -> Kind.HAS_ONE;
            default -> throw new IllegalArgumentException("unknown relation kind: " + raw);
        };
    }
}

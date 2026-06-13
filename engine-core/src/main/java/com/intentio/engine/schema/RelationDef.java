package com.intentio.engine.schema;

public record RelationDef(String name, Kind kind, String targetEntity, String fk, OnDelete onDelete) {

    public RelationDef {
        onDelete = onDelete == null ? OnDelete.NO_ACTION : onDelete;
    }

    public enum Kind { BELONGS_TO, HAS_MANY, HAS_ONE }

    /** 父行被删除时，对引用它的子行采取的动作。 */
    public enum OnDelete { NO_ACTION, CASCADE, RESTRICT, SET_NULL }

    public static Kind parseKind(String raw) {
        return switch (raw.toLowerCase()) {
            case "belongs_to" -> Kind.BELONGS_TO;
            case "has_many" -> Kind.HAS_MANY;
            case "has_one" -> Kind.HAS_ONE;
            default -> throw new IllegalArgumentException("unknown relation kind: " + raw);
        };
    }

    public static OnDelete parseOnDelete(String raw) {
        if (raw == null) return OnDelete.NO_ACTION;
        return switch (raw.toLowerCase()) {
            case "cascade" -> OnDelete.CASCADE;
            case "restrict" -> OnDelete.RESTRICT;
            case "set_null" -> OnDelete.SET_NULL;
            case "no_action", "" -> OnDelete.NO_ACTION;
            default -> throw new IllegalArgumentException("unknown on_delete action: " + raw);
        };
    }
}

package com.intentio.engine.schema;

import java.util.List;

public record FieldDef(
        String name,
        FieldType type,
        boolean required,
        boolean pk,
        boolean autoIncrement,
        Integer length,
        Integer precision,
        Integer scale,
        List<String> enumValues,
        Object defaultValue) {

    public FieldDef {
        enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
    }
}

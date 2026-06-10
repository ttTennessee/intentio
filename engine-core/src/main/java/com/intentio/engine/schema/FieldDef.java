package com.intentio.engine.schema;

import java.util.List;

public final class FieldDef {
    private final String name;
    private final FieldType type;
    private final boolean required;
    private final boolean pk;
    private final boolean autoIncrement;
    private final Integer length;
    private final Integer precision;
    private final Integer scale;
    private final List<String> enumValues;
    private final Object defaultValue;

    public FieldDef(String name, FieldType type, boolean required, boolean pk, boolean autoIncrement,
                    Integer length, Integer precision, Integer scale,
                    List<String> enumValues, Object defaultValue) {
        this.name = name;
        this.type = type;
        this.required = required;
        this.pk = pk;
        this.autoIncrement = autoIncrement;
        this.length = length;
        this.precision = precision;
        this.scale = scale;
        this.enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
        this.defaultValue = defaultValue;
    }

    public String name() { return name; }
    public FieldType type() { return type; }
    public boolean required() { return required; }
    public boolean pk() { return pk; }
    public boolean autoIncrement() { return autoIncrement; }
    public Integer length() { return length; }
    public Integer precision() { return precision; }
    public Integer scale() { return scale; }
    public List<String> enumValues() { return enumValues; }
    public Object defaultValue() { return defaultValue; }
}

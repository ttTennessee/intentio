package com.intentio.engine.intent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ReferenceParser {

    public record Ref(String opName, String field) {
        public static Ref of(String opName) { return new Ref(opName, "id"); }
    }

    private static final Pattern PATTERN = Pattern.compile("^@([A-Za-z_][A-Za-z0-9_]*)(?:\\.([A-Za-z_][A-Za-z0-9_]*))?$");

    private ReferenceParser() {}

    public static boolean isReference(Object value) {
        return value instanceof String s && s.startsWith("@") && PATTERN.matcher(s).matches();
    }

    public static Ref parse(String value) {
        Matcher m = PATTERN.matcher(value);
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid reference syntax: " + value);
        }
        String opName = m.group(1);
        String field = m.group(2);
        return new Ref(opName, field == null ? "id" : field);
    }
}

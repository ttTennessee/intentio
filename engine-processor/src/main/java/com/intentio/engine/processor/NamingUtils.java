package com.intentio.engine.processor;

final class NamingUtils {

    private NamingUtils() {}

    static String toUpperSnake(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        StringBuilder sb = new StringBuilder(name.length() + 4);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '_') {
                sb.append('_');
            } else if (Character.isUpperCase(c)) {
                if (i > 0 && name.charAt(i - 1) != '_') {
                    sb.append('_');
                }
                sb.append(c);
            } else {
                sb.append(Character.toUpperCase(c));
            }
        }
        return sb.toString();
    }
}

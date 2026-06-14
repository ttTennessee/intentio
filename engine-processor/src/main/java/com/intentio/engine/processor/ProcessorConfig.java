package com.intentio.engine.processor;

import java.nio.file.Path;
import java.util.Map;

public record ProcessorConfig(
        boolean enabled,
        Path schemaDir,
        String outputPackage,
        String classSuffix,
        String fieldNameStyle,
        boolean allInTablesEnabled) {

    private static final String PREFIX = "intentio.processor.";

    public static ProcessorConfig fromOptions(Map<String, String> options) {
        boolean enabled = !"false".equalsIgnoreCase(options.get(PREFIX + "enable"));
        String schemaDir = options.get(PREFIX + "schema.dir");
        String outputPackage = options.get(PREFIX + "output.package");
        String classSuffix = options.getOrDefault(PREFIX + "tableDef.classSuffix", "TableDef");
        String fieldNameStyle = options.getOrDefault(PREFIX + "tableDef.fieldNameStyle", "upperCase");
        boolean allInTables = "true".equalsIgnoreCase(options.get(PREFIX + "allInTables.enable"));

        if (!enabled) {
            return new ProcessorConfig(false, null, null, classSuffix, fieldNameStyle, allInTables);
        }
        if (schemaDir == null || schemaDir.isBlank()) {
            throw new IllegalArgumentException("Missing required option: " + PREFIX + "schema.dir");
        }
        if (outputPackage == null || outputPackage.isBlank()) {
            throw new IllegalArgumentException("Missing required option: " + PREFIX + "output.package");
        }
        if (!"upperCase".equals(fieldNameStyle)) {
            throw new IllegalArgumentException("Unsupported fieldNameStyle: " + fieldNameStyle + " (v1 supports upperCase only)");
        }

        return new ProcessorConfig(
                true,
                Path.of(schemaDir),
                outputPackage,
                classSuffix,
                fieldNameStyle,
                allInTables);
    }
}

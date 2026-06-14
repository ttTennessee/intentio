package com.intentio.engine.processor;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessorConfigTest {

    @Test
    void disabledWhenEnableFalse() {
        ProcessorConfig config = ProcessorConfig.fromOptions(Map.of("intentio.processor.enable", "false"));
        assertFalse(config.enabled());
    }

    @Test
    void requiresSchemaDirAndOutputPackage() {
        assertThrows(IllegalArgumentException.class, () ->
                ProcessorConfig.fromOptions(Map.of("intentio.processor.enable", "true")));
    }

    @Test
    void parsesRequiredOptions() {
        ProcessorConfig config = ProcessorConfig.fromOptions(Map.of(
                "intentio.processor.schema.dir", "/tmp/schema",
                "intentio.processor.output.package", "com.example.schema"));

        assertTrue(config.enabled());
        assertTrue(config.schemaDir().endsWith("schema"));
        assertTrue(config.outputPackage().equals("com.example.schema"));
        assertTrue(config.classSuffix().equals("TableDef"));
        assertFalse(config.allInTablesEnabled());
    }
}

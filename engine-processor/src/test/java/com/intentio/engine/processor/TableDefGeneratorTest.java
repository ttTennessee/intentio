package com.intentio.engine.processor;

import com.intentio.engine.schema.SchemaLoader;
import com.intentio.engine.schema.SchemaRegistry;
import com.squareup.javapoet.JavaFile;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TableDefGeneratorTest {

    @Test
    void generatesEntityTableFieldAndRelationConstants() throws IOException {
        Path schemaDir = Path.of("../engine-core/src/test/resources/test-schema-ext").toAbsolutePath().normalize();
        SchemaRegistry registry = SchemaLoader.loadDirectory(schemaDir);

        ProcessorConfig config = new ProcessorConfig(
                true,
                schemaDir,
                "com.intentio.test.schema",
                "TableDef",
                "upperCase",
                false);

        List<JavaFile> files = new TableDefGenerator().generate(registry, config);
        StringBuilder all = new StringBuilder();
        for (JavaFile file : files) {
            file.writeTo(all);
        }
        String source = all.toString();

        assertTrue(source.contains("class AccountTableDef"));
        assertTrue(source.contains("ENTITY = \"Account\""));
        assertTrue(source.contains("TABLE = \"account\""));
        assertTrue(source.contains("PK = \"id\""));
        assertTrue(source.contains("class Fields"));
        assertTrue(source.contains("USERNAME = \"username\""));
        assertTrue(source.contains("JOINED_ON = \"joined_on\""));
        assertTrue(source.contains("class Relations"));
        assertTrue(source.contains("POSTS = \"posts\""));
    }

    @Test
    void generatesTablesAggregatorWhenEnabled() throws IOException {
        Path schemaDir = Path.of("../engine-core/src/test/resources/test-schema-ext").toAbsolutePath().normalize();
        SchemaRegistry registry = SchemaLoader.loadDirectory(schemaDir);

        ProcessorConfig config = new ProcessorConfig(
                true,
                schemaDir,
                "com.intentio.test.schema",
                "TableDef",
                "upperCase",
                true);

        List<JavaFile> files = new TableDefGenerator().generate(registry, config);
        StringBuilder all = new StringBuilder();
        for (JavaFile file : files) {
            file.writeTo(all);
        }
        String source = all.toString();

        assertTrue(source.contains("class Tables"));
        assertTrue(source.contains("AccountTableDef.ENTITY"));
    }
}

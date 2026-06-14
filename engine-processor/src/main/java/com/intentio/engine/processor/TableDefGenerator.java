package com.intentio.engine.processor;

import com.intentio.engine.schema.EntityDef;
import com.intentio.engine.schema.SchemaRegistry;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TableDefGenerator {

    public List<JavaFile> generate(SchemaRegistry registry, ProcessorConfig config) {
        List<JavaFile> files = new ArrayList<>();
        List<EntityDef> entities = new ArrayList<>(registry.entities());

        for (EntityDef entity : entities) {
            files.add(buildTableDef(entity, config));
        }

        if (config.allInTablesEnabled() && !entities.isEmpty()) {
            files.add(buildTablesAggregator(entities, config));
        }

        return files;
    }

    private JavaFile buildTableDef(EntityDef entity, ProcessorConfig config) {
        String className = entity.name() + config.classSuffix();
        TypeSpec.Builder type = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(privateConstructor());

        type.addField(stringField("ENTITY", entity.name(), "Intentio entity name for QueryIntent.from(...)"));
        type.addField(stringField("TABLE", entity.table(), "Database table name"));

        entity.primaryKey().ifPresent(pk ->
                type.addField(stringField("PK", pk.name(), "Primary key field name")));

        if (!entity.fields().isEmpty()) {
            type.addType(buildFieldsType(entity));
        }

        if (!entity.relations().isEmpty()) {
            type.addType(buildRelationsType(entity));
        }

        return JavaFile.builder(config.outputPackage(), type.build())
                .skipJavaLangImports(true)
                .build();
    }

    private TypeSpec buildFieldsType(EntityDef entity) {
        TypeSpec.Builder fields = TypeSpec.classBuilder("Fields")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .addMethod(privateConstructor());

        for (Map.Entry<String, ?> entry : entity.fields().entrySet()) {
            String fieldName = entry.getKey();
            fields.addField(stringField(
                    NamingUtils.toUpperSnake(fieldName),
                    fieldName,
                    null));
        }

        return fields.build();
    }

    private TypeSpec buildRelationsType(EntityDef entity) {
        TypeSpec.Builder relations = TypeSpec.classBuilder("Relations")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .addMethod(privateConstructor());

        for (String relationName : entity.relations().keySet()) {
            relations.addField(stringField(
                    NamingUtils.toUpperSnake(relationName),
                    relationName,
                    null));
        }

        return relations.build();
    }

    private JavaFile buildTablesAggregator(List<EntityDef> entities, ProcessorConfig config) {
        TypeSpec.Builder type = TypeSpec.classBuilder("Tables")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(privateConstructor());

        for (EntityDef entity : entities) {
            String tableDefClass = entity.name() + config.classSuffix();
            type.addField(FieldSpec.builder(String.class, entity.name(), Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .initializer("$T.ENTITY", ClassName.get(config.outputPackage(), tableDefClass))
                    .build());
        }

        return JavaFile.builder(config.outputPackage(), type.build())
                .skipJavaLangImports(true)
                .build();
    }

    private static FieldSpec stringField(String constantName, String value, String javadoc) {
        FieldSpec.Builder builder = FieldSpec.builder(String.class, constantName, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("$S", value);
        if (javadoc != null) {
            builder.addJavadoc(javadoc);
        }
        return builder.build();
    }

    private static MethodSpec privateConstructor() {
        return MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .build();
    }
}

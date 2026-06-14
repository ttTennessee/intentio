package com.intentio.engine.processor;

import com.google.auto.service.AutoService;
import com.intentio.engine.schema.SchemaLoader;
import com.intentio.engine.schema.SchemaRegistry;
import com.squareup.javapoet.JavaFile;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.Set;

@AutoService(Processor.class)
@SupportedAnnotationTypes("*")
@SupportedOptions({
        "intentio.processor.enable",
        "intentio.processor.schema.dir",
        "intentio.processor.output.package",
        "intentio.processor.tableDef.classSuffix",
        "intentio.processor.tableDef.fieldNameStyle",
        "intentio.processor.allInTables.enable"
})
public class SchemaTableDefProcessor extends AbstractProcessor {

    private boolean generated;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (generated) {
            return false;
        }
        generated = true;

        ProcessorConfig config;
        try {
            config = ProcessorConfig.fromOptions(processingEnv.getOptions());
        } catch (IllegalArgumentException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage());
            return false;
        }

        if (!config.enabled()) {
            return false;
        }

        SchemaRegistry registry;
        try {
            registry = SchemaLoader.loadDirectory(config.schemaDir());
        } catch (RuntimeException e) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "Failed to load schema from " + config.schemaDir() + ": " + e.getMessage());
            return false;
        }

        TableDefGenerator generator = new TableDefGenerator();
        Filer filer = processingEnv.getFiler();

        for (JavaFile file : generator.generate(registry, config)) {
            try {
                file.writeTo(filer);
            } catch (FilerException e) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.WARNING,
                        "Skip existing generated file: " + file.packageName + "." + file.typeSpec.name);
            } catch (IOException e) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "Failed to write " + file.packageName + "." + file.typeSpec.name + ": " + e.getMessage());
            }
        }

        return false;
    }
}

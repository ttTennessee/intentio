package com.intentio.engine.schema;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class SchemaLoader {

    private SchemaLoader() {}

    public static SchemaRegistry loadDirectory(Path dir) {
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Not a directory: " + dir);
        }
        Map<String, EntityDef> entities = new LinkedHashMap<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith(".yml") || p.toString().endsWith(".yaml"))
                .sorted()
                .forEach(p -> mergeEntities(entities, loadFile(p)));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read schema dir: " + dir, e);
        }
        return SchemaRegistry.of(entities);
    }

    public static SchemaRegistry loadClasspath(String classpathDir) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Map<String, EntityDef> entities = new LinkedHashMap<>();
        InputStream listing = cl.getResourceAsStream(classpathDir);
        if (listing == null) {
            throw new IllegalArgumentException("Classpath dir not found: " + classpathDir);
        }
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(listing))) {
            String line;
            List<String> files = new ArrayList<>();
            while ((line = reader.readLine()) != null) {
                if (line.endsWith(".yml") || line.endsWith(".yaml")) {
                    files.add(line);
                }
            }
            files.sort(String::compareTo);
            for (String f : files) {
                try (InputStream in = cl.getResourceAsStream(classpathDir + "/" + f)) {
                    if (in == null) continue;
                    mergeEntities(entities, parseYaml(in, classpathDir + "/" + f));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read classpath schema: " + classpathDir, e);
        }
        return SchemaRegistry.of(entities);
    }

    private static Map<String, EntityDef> loadFile(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            return parseYaml(in, path.toString());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read schema file: " + path, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, EntityDef> parseYaml(InputStream in, String source) {
        Yaml yaml = new Yaml();
        Object root = yaml.load(in);
        if (!(root instanceof Map<?, ?> rootMap)) {
            throw new IllegalArgumentException("Schema file must have a map root: " + source);
        }
        Object entityNode = rootMap.get("entity");
        if (!(entityNode instanceof Map<?, ?> entityMap)) {
            throw new IllegalArgumentException("Missing 'entity:' block in " + source);
        }
        Map<String, EntityDef> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : entityMap.entrySet()) {
            String name = e.getKey().toString();
            Map<String, Object> body = (Map<String, Object>) e.getValue();
            result.put(name, parseEntity(name, body, source));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static EntityDef parseEntity(String name, Map<String, Object> body, String source) {
        String table = (String) body.getOrDefault("table", camelToSnake(name));

        Map<String, FieldDef> fields = new LinkedHashMap<>();
        Map<String, Object> fieldNodes = (Map<String, Object>) body.getOrDefault("fields", Map.of());
        for (Map.Entry<String, Object> fe : fieldNodes.entrySet()) {
            fields.put(fe.getKey(), parseField(fe.getKey(), (Map<String, Object>) fe.getValue()));
        }

        Map<String, RelationDef> relations = new LinkedHashMap<>();
        Map<String, Object> relationNodes = (Map<String, Object>) body.getOrDefault("relations", Map.of());
        for (Map.Entry<String, Object> re : relationNodes.entrySet()) {
            relations.put(re.getKey(), parseRelation(re.getKey(), (Map<String, Object>) re.getValue()));
        }

        IntegrityDef integrity = parseIntegrity((Map<String, Object>) body.get("integrity"));

        return new EntityDef(name, table, fields, relations, integrity);
    }

    private static FieldDef parseField(String name, Map<String, Object> body) {
        FieldType type = FieldType.parse((String) body.get("type"));
        boolean required = Boolean.TRUE.equals(body.get("required"));
        boolean pk = Boolean.TRUE.equals(body.get("pk"));
        boolean autoIncrement = Boolean.TRUE.equals(body.get("auto_increment"));
        Integer length = toInt(body.get("length"));
        Integer precision = toInt(body.get("precision"));
        Integer scale = toInt(body.get("scale"));
        List<String> enumValues = null;
        Object values = body.get("values");
        if (values instanceof List<?> list) {
            enumValues = new ArrayList<>();
            for (Object o : list) enumValues.add(String.valueOf(o));
        }
        Object defaultValue = body.get("default");
        return new FieldDef(name, type, required, pk, autoIncrement, length, precision, scale, enumValues, defaultValue);
    }

    private static RelationDef parseRelation(String name, Map<String, Object> body) {
        RelationDef.Kind kind = RelationDef.parseKind((String) body.get("kind"));
        String entity = (String) body.get("entity");
        String fk = (String) body.get("fk");
        return new RelationDef(name, kind, entity, fk);
    }

    @SuppressWarnings("unchecked")
    private static IntegrityDef parseIntegrity(Map<String, Object> body) {
        if (body == null) return IntegrityDef.empty();
        List<Rule> onCreate = parseRuleList(body.get("on_create"));
        List<Rule> onUpdate = parseRuleList(body.get("on_update"));
        List<Rule> onDelete = parseRuleList(body.get("on_delete"));

        Map<String, Rule> fieldRules = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            String key = entry.getKey();
            if (key.equals("on_create") || key.equals("on_update") || key.equals("on_delete")) continue;
            if (entry.getValue() instanceof Map<?, ?> ruleBody) {
                Map<String, Object> bodyMap = (Map<String, Object>) ruleBody;
                String message = (String) bodyMap.get("message");
                Map<String, Object> params = new LinkedHashMap<>(bodyMap);
                params.remove("message");
                fieldRules.put(key, new Rule(Rule.Type.EXPRESSION, params, message));
            }
        }
        return new IntegrityDef(onCreate, onUpdate, onDelete, fieldRules);
    }

    @SuppressWarnings("unchecked")
    private static List<Rule> parseRuleList(Object node) {
        if (node == null) return List.of();
        if (!(node instanceof Map<?, ?> map)) return List.of();
        List<Rule> rules = new ArrayList<>();
        Map<String, Object> body = (Map<String, Object>) map;
        for (Map.Entry<String, Object> e : body.entrySet()) {
            String key = e.getKey();
            switch (key) {
                case "require_has" -> {
                    String relationName = String.valueOf(e.getValue());
                    Integer min = toInt(body.get("min"));
                    Map<String, Object> params = new LinkedHashMap<>();
                    params.put("relation", relationName);
                    if (min != null) params.put("min", min);
                    rules.add(new Rule(Rule.Type.REQUIRE_HAS, params, null));
                }
                case "min" -> { /* attached to require_has above */ }
                case "stock_check" -> {
                    Map<String, Object> params = (Map<String, Object>) e.getValue();
                    String message = (String) params.get("message");
                    rules.add(new Rule(Rule.Type.STOCK_CHECK, params, message));
                }
                case "ref_check" -> {
                    Map<String, Object> params = (Map<String, Object>) e.getValue();
                    String message = (String) params.get("message");
                    rules.add(new Rule(Rule.Type.REF_CHECK, params, message));
                }
                default -> { /* ignore unknown */ }
            }
        }
        return rules;
    }

    private static void mergeEntities(Map<String, EntityDef> sink, Map<String, EntityDef> incoming) {
        for (Map.Entry<String, EntityDef> e : incoming.entrySet()) {
            if (sink.containsKey(e.getKey())) {
                throw new IllegalStateException("Duplicate entity definition: " + e.getKey());
            }
            sink.put(e.getKey(), e.getValue());
        }
    }

    private static Integer toInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        return Integer.parseInt(o.toString());
    }

    private static String camelToSnake(String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) sb.append('_');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}

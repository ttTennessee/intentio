package com.intentio.engine;

import com.intentio.engine.constraint.ConstraintEngine;
import com.intentio.engine.execute.Executor;
import com.intentio.engine.execute.TopologySorter;
import com.intentio.engine.intent.IntentGroup;
import com.intentio.engine.intent.Op;
import com.intentio.engine.intent.QueryIntent;
import com.intentio.engine.query.QueryExecutor;
import com.intentio.engine.result.IntentError;
import com.intentio.engine.result.IntentResult;
import com.intentio.engine.result.QueryResult;
import com.intentio.engine.schema.SchemaLoader;
import com.intentio.engine.schema.SchemaRegistry;
import com.intentio.engine.validate.Validator;
import com.intentio.engine.vm.PendingRow;
import com.intentio.engine.vm.ReferenceResolver;
import com.intentio.engine.vm.VirtualMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SchemaEngine {

    private static final Logger log = LoggerFactory.getLogger(SchemaEngine.class);

    private final SchemaRegistry registry;
    private final DataSource dataSource;
    private final Validator validator;
    private final Executor executor;
    private final QueryExecutor queryExecutor;

    private SchemaEngine(SchemaRegistry registry, DataSource dataSource) {
        this.registry = registry;
        this.dataSource = dataSource;
        this.validator = new Validator(registry);
        this.executor = new Executor(registry);
        this.queryExecutor = new QueryExecutor(registry);
    }

    public static SchemaEngine load(Path schemaDir, DataSource dataSource) {
        SchemaRegistry reg = SchemaLoader.loadDirectory(schemaDir);
        return new SchemaEngine(reg, dataSource);
    }

    public static SchemaEngine loadClasspath(String classpathDir, DataSource dataSource) {
        SchemaRegistry reg = SchemaLoader.loadClasspath(classpathDir);
        return new SchemaEngine(reg, dataSource);
    }

    public SchemaRegistry registry() { return registry; }

    public IntentResult execute(IntentGroup group) {
        List<IntentError> fieldErrors = validator.validate(group);
        if (!fieldErrors.isEmpty()) {
            return IntentResult.failure(fieldErrors);
        }

        List<Op> sorted;
        try {
            sorted = TopologySorter.sort(group);
        } catch (RuntimeException e) {
            return IntentResult.failure(List.of(
                IntentError.of(null, null, "topology", e.getMessage())));
        }

        VirtualMachine vm = new VirtualMachine(registry);
        ReferenceResolver resolver = new ReferenceResolver(vm);
        ConstraintEngine constraints = new ConstraintEngine(registry, vm);
        Map<String, Object> generatedIds = new LinkedHashMap<>();

        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);

            Map<Op, Map<String, Object>> resolvedByOp = new LinkedHashMap<>();
            for (Op op : sorted) {
                Map<String, Object> resolved;
                try {
                    resolved = resolver.resolve(op);
                } catch (RuntimeException e) {
                    conn.rollback();
                    return IntentResult.failure(List.of(
                        IntentError.of(op.name(), op.entity(), "reference", e.getMessage())));
                }
                PendingRow row = new PendingRow(op.entity(), resolved);
                vm.register(op.name(), row);
                resolvedByOp.put(op, resolved);

                Optional<IntentError> err = constraints.checkPre(op, resolved, conn);
                if (err.isPresent()) {
                    conn.rollback();
                    return IntentResult.failure(List.of(err.get()));
                }

                Object generated = executor.execute(op, resolved, conn);
                if (generated != null) {
                    row.setGeneratedId(generated);
                    generatedIds.put(op.name(), generated);
                }
            }

            for (var entry : resolvedByOp.entrySet()) {
                Optional<IntentError> err = constraints.checkPost(entry.getKey(), entry.getValue(), conn);
                if (err.isPresent()) {
                    conn.rollback();
                    return IntentResult.failure(List.of(err.get()));
                }
            }
            conn.commit();
            return IntentResult.success(generatedIds);
        } catch (Exception e) {
            log.warn("execute failed", e);
            if (conn != null) try { conn.rollback(); } catch (Exception ignore) {}
            return IntentResult.failure(List.of(
                IntentError.of(null, null, "execute", e.getMessage())));
        } finally {
            if (conn != null) try { conn.setAutoCommit(true); conn.close(); } catch (Exception ignore) {}
        }
    }

    public QueryResult query(QueryIntent intent) {
        try (Connection conn = dataSource.getConnection()) {
            return queryExecutor.run(intent, conn);
        } catch (Exception e) {
            throw new RuntimeException("query failed: " + e.getMessage(), e);
        }
    }
}

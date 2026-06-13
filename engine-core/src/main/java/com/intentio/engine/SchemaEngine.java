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
import com.intentio.engine.result.PageResult;
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
import java.util.stream.Collectors;

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
        log.info("execute start: {} ops [{}]", group.ops().size(), summarizeOps(group.ops()));

        List<IntentError> fieldErrors = validator.validate(group);
        if (!fieldErrors.isEmpty()) {
            log.warn("validation failed: {}", fieldErrors);
            return IntentResult.failure(fieldErrors);
        }

        List<Op> sorted;
        try {
            sorted = TopologySorter.sort(group);
        } catch (RuntimeException e) {
            log.warn("topology sort failed: {}", e.getMessage());
            return IntentResult.failure(List.of(
                IntentError.of(null, null, "topology", e.getMessage())));
        }
        log.debug("execute order: {}", summarizeOps(sorted));

        VirtualMachine vm = new VirtualMachine(registry);
        ReferenceResolver resolver = new ReferenceResolver(vm);
        ConstraintEngine constraints = new ConstraintEngine(registry, vm);
        Map<String, Object> generatedIds = new LinkedHashMap<>();

        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);
            log.debug("transaction opened");

            Map<Op, Map<String, Object>> resolvedByOp = new LinkedHashMap<>();
            for (Op op : sorted) {
                log.debug("processing op {} type={} entity={}", op.name(), op.type(), op.entity());
                Map<String, Object> resolved;
                try {
                    resolved = resolver.resolve(op);
                } catch (RuntimeException e) {
                    log.warn("reference resolve failed for op {}: {}", op.name(), e.getMessage());
                    conn.rollback();
                    log.warn("transaction rolled back");
                    return IntentResult.failure(List.of(
                        IntentError.of(op.name(), op.entity(), "reference", e.getMessage())));
                }
                log.debug("resolved fields for {}: {}", op.name(), resolved);
                PendingRow row = new PendingRow(op.entity(), resolved);
                vm.register(op.name(), row);
                resolvedByOp.put(op, resolved);

                Optional<IntentError> err = constraints.checkPre(op, resolved, conn);
                if (err.isPresent()) {
                    log.warn("pre-constraint failed, rolling back: {}", err.get());
                    conn.rollback();
                    log.warn("transaction rolled back");
                    return IntentResult.failure(List.of(err.get()));
                }

                Object generated = executor.execute(op, resolved, conn);
                if (generated != null) {
                    row.setGeneratedId(generated);
                    generatedIds.put(op.name(), generated);
                    log.debug("generated id for {}: {}", op.name(), generated);
                }
            }

            for (var entry : resolvedByOp.entrySet()) {
                Optional<IntentError> err = constraints.checkPost(entry.getKey(), entry.getValue(), conn);
                if (err.isPresent()) {
                    log.warn("post-constraint failed, rolling back: {}", err.get());
                    conn.rollback();
                    log.warn("transaction rolled back");
                    return IntentResult.failure(List.of(err.get()));
                }
            }
            conn.commit();
            log.info("execute committed, generatedIds={}", generatedIds);
            return IntentResult.success(generatedIds);
        } catch (Exception e) {
            log.warn("execute failed", e);
            if (conn != null) try { conn.rollback(); log.warn("transaction rolled back"); } catch (Exception ignore) {}
            return IntentResult.failure(List.of(
                IntentError.of(null, null, "execute", e.getMessage())));
        } finally {
            if (conn != null) try { conn.setAutoCommit(true); conn.close(); } catch (Exception ignore) {}
        }
    }

    public QueryResult query(QueryIntent intent) {
        log.info("query start: entity={} includes={} filters={} limit={} offset={}",
            intent.entity(), intent.includes(), intent.filters(), intent.limit(), intent.offset());
        try (Connection conn = dataSource.getConnection()) {
            QueryResult result = queryExecutor.run(intent, conn);
            log.info("query done: entity={} rows={}", intent.entity(), result.rows().size());
            return result;
        } catch (Exception e) {
            log.warn("query failed: entity={}", intent.entity(), e);
            throw new RuntimeException("query failed: " + e.getMessage(), e);
        }
    }

    /** 分页查询：返回 {rows, total}。total 用独立 COUNT(*) SQL 计算,不受 limit/offset 影响。 */
    public PageResult queryPage(QueryIntent intent) {
        log.info("queryPage start: entity={} filters={} limit={} offset={}",
            intent.entity(), intent.filters(), intent.limit(), intent.offset());
        try (Connection conn = dataSource.getConnection()) {
            PageResult result = queryExecutor.runPage(intent, conn);
            log.info("queryPage done: entity={} rows={} total={}",
                intent.entity(), result.size(), result.total());
            return result;
        } catch (Exception e) {
            log.warn("queryPage failed: entity={}", intent.entity(), e);
            throw new RuntimeException("queryPage failed: " + e.getMessage(), e);
        }
    }

    private static String summarizeOps(List<Op> ops) {
        return ops.stream()
            .map(op -> op.name() + ":" + op.type() + "@" + op.entity())
            .collect(Collectors.joining(", "));
    }
}

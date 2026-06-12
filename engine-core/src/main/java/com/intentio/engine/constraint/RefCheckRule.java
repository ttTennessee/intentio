package com.intentio.engine.constraint;

import com.intentio.engine.intent.Op;
import com.intentio.engine.logging.SqlLogger;
import com.intentio.engine.result.IntentError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.intentio.engine.schema.EntityDef;
import com.intentio.engine.schema.Rule;
import com.intentio.engine.schema.SchemaRegistry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

public final class RefCheckRule {

    private static final Logger log = LoggerFactory.getLogger(RefCheckRule.class);

    private final SchemaRegistry registry;

    public RefCheckRule(SchemaRegistry registry) {
        this.registry = registry;
    }

    public Optional<IntentError> check(Op op, Rule rule, Map<String, Object> resolvedFields, Connection conn) {
        String entity = (String) rule.param("entity");
        String field = (String) rule.param("field");
        Object value = resolvedFields.get(field);
        if (value == null) return Optional.empty();
        if (!existsInDb(conn, entity, value)) {
            return Optional.of(IntentError.of(op.name(), op.entity(), "ref_check",
                rule.message() != null ? rule.message()
                    : "Referenced " + entity + "(id=" + value + ") does not exist"));
        }
        return Optional.empty();
    }

    public boolean existsInDb(Connection conn, String entity, Object id) {
        EntityDef def = registry.require(entity);
        String sql = "SELECT 1 FROM " + def.table() + " WHERE id = ?";
        SqlLogger.sql(log, "REF_CHECK " + entity, sql, id);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("ref_check failed", e);
        }
    }
}

package com.intentio.engine.vm;

import com.intentio.engine.logging.SqlLogger;
import com.intentio.engine.schema.EntityDef;
import com.intentio.engine.schema.RelationDef;
import com.intentio.engine.schema.SchemaRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class VirtualMachine {

    private static final Logger log = LoggerFactory.getLogger(VirtualMachine.class);

    private final SchemaRegistry registry;
    private final Map<String, PendingRow> pending = new LinkedHashMap<>();

    public VirtualMachine(SchemaRegistry registry) {
        this.registry = registry;
    }

    public void register(String opName, PendingRow row) {
        pending.put(opName, row);
    }

    public PendingRow get(String opName) {
        return pending.get(opName);
    }

    public boolean has(String opName) {
        return pending.containsKey(opName);
    }

    public int countRelatedPending(String parentEntity, Object parentId, String parentRelation) {
        EntityDef parent = registry.require(parentEntity);
        RelationDef rel = parent.relation(parentRelation)
            .orElseThrow(() -> new IllegalArgumentException(
                "No relation " + parentRelation + " on " + parentEntity));
        if (rel.kind() != RelationDef.Kind.HAS_MANY && rel.kind() != RelationDef.Kind.HAS_ONE) {
            return 0;
        }
        int count = 0;
        for (PendingRow row : pending.values()) {
            if (!row.entity().equals(rel.targetEntity())) continue;
            Object fk = row.value(rel.fk());
            if (Objects.equals(fk, parentId)) {
                count++;
            }
        }
        return count;
    }

    public int countRelatedDb(Connection conn, String parentEntity, Object parentId, String parentRelation) {
        EntityDef parent = registry.require(parentEntity);
        RelationDef rel = parent.relation(parentRelation).orElseThrow();
        EntityDef child = registry.require(rel.targetEntity());
        String sql = "SELECT COUNT(*) FROM " + child.table() + " WHERE " + rel.fk() + " = ?";
        SqlLogger.sql(log, "COUNT_RELATED " + parentEntity + "." + parentRelation, sql, parentId);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, parentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count related: " + e.getMessage(), e);
        }
        return 0;
    }

    public boolean existsInDb(DataSource ds, String entity, Object id) {
        EntityDef def = registry.require(entity);
        String sql = "SELECT 1 FROM " + def.table() + " WHERE id = ?";
        SqlLogger.sql(log, "EXISTS " + entity, sql, id);
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("exists check failed", e);
        }
    }
}

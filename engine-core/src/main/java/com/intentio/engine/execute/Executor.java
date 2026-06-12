package com.intentio.engine.execute;

import com.intentio.engine.intent.Op;
import com.intentio.engine.logging.SqlLogger;
import com.intentio.engine.schema.EntityDef;
import com.intentio.engine.schema.FieldDef;
import com.intentio.engine.schema.FieldType;
import com.intentio.engine.schema.SchemaRegistry;
import com.intentio.engine.validate.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Executor {

    private static final Logger log = LoggerFactory.getLogger(Executor.class);

    private final SchemaRegistry registry;

    public Executor(SchemaRegistry registry) {
        this.registry = registry;
    }

    public Object execute(Op op, Map<String, Object> resolvedFields, Connection conn) throws SQLException {
        return switch (op.type()) {
            case INSERT -> doInsert(op, resolvedFields, conn);
            case UPDATE -> { doUpdate(op, resolvedFields, conn); yield op.targetId(); }
            case DELETE -> { doDelete(op, conn); yield op.targetId(); }
        };
    }

    private Object doInsert(Op op, Map<String, Object> fields, Connection conn) throws SQLException {
        EntityDef entity = registry.require(op.entity());
        Map<String, Object> toInsert = new LinkedHashMap<>(fields);
        for (FieldDef def : entity.fields().values()) {
            if (def.pk() && def.autoIncrement()) toInsert.remove(def.name());
            if (!toInsert.containsKey(def.name()) && def.defaultValue() != null) {
                toInsert.put(def.name(), def.defaultValue());
            }
        }
        List<String> cols = new ArrayList<>(toInsert.keySet());
        String placeholders = String.join(", ", java.util.Collections.nCopies(cols.size(), "?"));
        String sql = "INSERT INTO " + entity.table()
            + " (" + String.join(", ", cols) + ") VALUES (" + placeholders + ")";

        List<Object> params = cols.stream().map(toInsert::get).toList();
        SqlLogger.sql(log, opLabel(op, "INSERT"), sql, params);

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int i = 1;
            for (String col : cols) {
                bindParam(ps, i++, toInsert.get(col), entity.fields().get(col));
            }
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        return null;
    }

    private void doUpdate(Op op, Map<String, Object> fields, Connection conn) throws SQLException {
        EntityDef entity = registry.require(op.entity());
        List<String> cols = new ArrayList<>(fields.keySet());
        String setClause = String.join(", ", cols.stream().map(c -> c + " = ?").toList());
        String sql = "UPDATE " + entity.table() + " SET " + setClause + " WHERE id = ?";
        List<Object> params = new ArrayList<>(cols.stream().map(fields::get).toList());
        params.add(op.targetId());
        SqlLogger.sql(log, opLabel(op, "UPDATE"), sql, params);

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            for (String col : cols) bindParam(ps, i++, fields.get(col), entity.fields().get(col));
            ps.setObject(i, op.targetId());
            ps.executeUpdate();
        }
    }

    private void doDelete(Op op, Connection conn) throws SQLException {
        EntityDef entity = registry.require(op.entity());
        String sql = "DELETE FROM " + entity.table() + " WHERE id = ?";
        SqlLogger.sql(log, opLabel(op, "DELETE"), sql, op.targetId());

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, op.targetId());
            ps.executeUpdate();
        }
    }

    private static String opLabel(Op op, String action) {
        return action + " " + op.name() + "@" + op.entity();
    }

    private void bindParam(PreparedStatement ps, int idx, Object value, FieldDef def) throws SQLException {
        if (value == null) { ps.setNull(idx, Types.NULL); return; }
        if (def == null) { ps.setObject(idx, value); return; }
        switch (def.type()) {
            case LONG -> ps.setLong(idx, value instanceof Number n ? n.longValue() : Long.parseLong(value.toString()));
            case STRING, ENUM -> ps.setString(idx, value.toString());
            case DECIMAL -> {
                BigDecimal bd = Validator.toBigDecimal(value);
                ps.setBigDecimal(idx, bd);
            }
            case DATE -> {
                LocalDate d = value instanceof LocalDate ld ? ld : LocalDate.parse(value.toString());
                ps.setDate(idx, java.sql.Date.valueOf(d));
            }
            case DATETIME -> ps.setString(idx, value.toString());
            case BOOLEAN -> {
                boolean b = value instanceof Boolean bb ? bb : Boolean.parseBoolean(value.toString());
                ps.setBoolean(idx, b);
            }
        }
        if (def.type() == FieldType.LONG && !(value instanceof Number)) {
            // already handled
        }
    }
}

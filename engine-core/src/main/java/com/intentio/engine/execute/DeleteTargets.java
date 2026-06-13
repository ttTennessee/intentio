package com.intentio.engine.execute;

import com.intentio.engine.intent.Op;
import com.intentio.engine.logging.SqlLogger;
import com.intentio.engine.schema.EntityDef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 解析一个 DELETE op 实际命中的主键 id 集合：
 * 按 id 删 → 单元素；按条件删 → SELECT id WHERE 条件。
 * 供前置 RESTRICT 检查与执行期 CASCADE/SET_NULL 复用。
 */
public final class DeleteTargets {

    private static final Logger log = LoggerFactory.getLogger(DeleteTargets.class);

    private DeleteTargets() {}

    public static List<Object> resolveIds(EntityDef entity, Op op, Connection conn) throws SQLException {
        if (op.targetId() != null) {
            return new ArrayList<>(List.of(op.targetId()));
        }
        Map<String, Object> conds = op.conditions();
        if (conds.isEmpty()) {
            return new ArrayList<>();
        }
        StringBuilder sql = new StringBuilder("SELECT id FROM ").append(entity.table()).append(" WHERE ");
        List<Object> params = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, Object> e : conds.entrySet()) {
            requireColumn(entity, e.getKey());
            if (i++ > 0) sql.append(" AND ");
            sql.append(e.getKey()).append(" = ?");
            params.add(e.getValue());
        }
        SqlLogger.sql(log, "DELETE-RESOLVE " + entity.name(), sql.toString(), params);

        List<Object> ids = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int k = 0; k < params.size(); k++) ps.setObject(k + 1, params.get(k));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getObject(1));
            }
        }
        return ids;
    }

    private static void requireColumn(EntityDef entity, String field) {
        if (!entity.fields().containsKey(field)) {
            throw new IllegalArgumentException(
                "Unknown column '" + field + "' on entity " + entity.name());
        }
    }
}

package com.intentio.engine.query;

import com.intentio.engine.jdbc.JdbcValueCoercer;
import com.intentio.engine.intent.Filter;
import com.intentio.engine.intent.Order;
import com.intentio.engine.intent.QueryIntent;
import com.intentio.engine.logging.SqlLogger;
import com.intentio.engine.result.PageResult;
import com.intentio.engine.result.QueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.intentio.engine.schema.EntityDef;
import com.intentio.engine.schema.FieldDef;
import com.intentio.engine.schema.RelationDef;
import com.intentio.engine.schema.RelationGraph;
import com.intentio.engine.schema.SchemaRegistry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class QueryExecutor {

    private static final Logger log = LoggerFactory.getLogger(QueryExecutor.class);

    private final SchemaRegistry registry;

    public QueryExecutor(SchemaRegistry registry) {
        this.registry = registry;
    }

    public QueryResult run(QueryIntent intent, Connection conn) throws SQLException {
        EntityDef root = registry.require(intent.entity());
        String rootAlias = "t0";

        List<JoinNode> joinNodes = planJoins(intent);

        // 根实体显式投影列（空 = 全部可读列）；列名先做合法性/防注入校验。
        Set<String> rootSelect = new LinkedHashSet<>();
        for (String col : intent.select()) {
            requireColumn(root, col);
            rootSelect.add(col);
        }

        StringBuilder sql = new StringBuilder("SELECT ");
        List<ColumnRef> projection = new ArrayList<>();

        appendColumns(projection, sql, root, rootAlias, "", rootSelect);
        for (JoinNode j : joinNodes) {
            sql.append(", ");
            appendColumns(projection, sql, j.entity, j.alias, j.path, null);
        }

        sql.append(" FROM ").append(root.table()).append(" ").append(rootAlias);
        for (JoinNode j : joinNodes) {
            sql.append(" LEFT JOIN ").append(j.entity.table()).append(" ").append(j.alias);
            if (j.edge.kind() == RelationDef.Kind.BELONGS_TO) {
                sql.append(" ON ").append(j.alias).append(".id = ")
                    .append(j.parentAlias).append(".").append(j.edge.fk());
            } else {
                sql.append(" ON ").append(j.alias).append(".").append(j.edge.fk())
                    .append(" = ").append(j.parentAlias).append(".id");
            }
        }

        List<Object> params = new ArrayList<>();
        appendWhere(sql, params, root, rootAlias, intent);

        if (!intent.orders().isEmpty()) {
            sql.append(" ORDER BY ");
            List<Order> orders = intent.orders();
            for (int oi = 0; oi < orders.size(); oi++) {
                Order o = orders.get(oi);
                requireColumn(root, o.field());
                if (oi > 0) sql.append(", ");
                sql.append(rootAlias).append(".").append(o.field()).append(o.asc() ? " ASC" : " DESC");
            }
        } else {
            sql.append(" ORDER BY ").append(rootAlias).append(".id");
        }

        if (intent.limit() != null) sql.append(" LIMIT ").append(intent.limit());
        if (intent.offset() != null) sql.append(" OFFSET ").append(intent.offset());

        SqlLogger.sql(log, "QUERY " + intent.entity(), sql.toString(), params);

        Map<Object, Map<String, Object>> rootRows = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                while (rs.next()) {
                    foldRow(rootRows, rs, md, projection, joinNodes);
                }
            }
        }
        return new QueryResult(new ArrayList<>(rootRows.values()));
    }

    /**
     * 分页查询：先 count(剥 limit/offset/order/JOIN),再走 run() 取分页数据。
     * count 只走根表,因 filter 仅允许根列(requireColumn 强制)。
     */
    public PageResult runPage(QueryIntent intent, Connection conn) throws SQLException {
        EntityDef root = registry.require(intent.entity());
        String rootAlias = "t0";

        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM ")
            .append(root.table()).append(" ").append(rootAlias);
        List<Object> countParams = new ArrayList<>();
        appendWhere(countSql, countParams, root, rootAlias, intent);

        SqlLogger.sql(log, "COUNT " + intent.entity(), countSql.toString(), countParams);

        long total;
        try (PreparedStatement ps = conn.prepareStatement(countSql.toString())) {
            for (int i = 0; i < countParams.size(); i++) ps.setObject(i + 1, countParams.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                total = rs.next() ? rs.getLong(1) : 0L;
            }
        }

        QueryResult rows = total == 0
            ? new QueryResult(List.of())
            : run(intent, conn);
        return new PageResult(rows.rows(), total);
    }

    /** 根 filters 与 OR groups 一起拼 WHERE。组与根条件之间 AND,组内 OR。 */
    private void appendWhere(StringBuilder sql, List<Object> params,
                             EntityDef root, String rootAlias, QueryIntent intent) {
        List<Filter> filters = intent.filters();
        List<List<Filter>> orGroups = intent.orGroups();
        if (filters.isEmpty() && orGroups.isEmpty()) return;

        sql.append(" WHERE ");
        boolean first = true;
        for (Filter f : filters) {
            requireColumn(root, f.field());
            if (!first) sql.append(" AND ");
            appendCondition(sql, params, rootAlias, f);
            first = false;
        }
        for (List<Filter> group : orGroups) {
            if (group.isEmpty()) continue;
            if (!first) sql.append(" AND ");
            sql.append("(");
            for (int gi = 0; gi < group.size(); gi++) {
                Filter f = group.get(gi);
                requireColumn(root, f.field());
                if (gi > 0) sql.append(" OR ");
                appendCondition(sql, params, rootAlias, f);
            }
            sql.append(")");
            first = false;
        }
    }

    /** 列名合法性 + 防注入：必须是实体的真实列。 */
    private void requireColumn(EntityDef entity, String field) {
        if (!entity.fields().containsKey(field)) {
            throw new IllegalArgumentException(
                "Unknown column '" + field + "' on entity " + entity.name());
        }
    }

    private void appendCondition(StringBuilder sql, List<Object> params, String alias, Filter f) {
        String col = alias + "." + f.field();
        switch (f.op()) {
            case EQ -> { sql.append(col).append(" = ?"); params.add(f.value()); }
            case NE -> { sql.append(col).append(" <> ?"); params.add(f.value()); }
            case GT -> { sql.append(col).append(" > ?"); params.add(f.value()); }
            case GTE -> { sql.append(col).append(" >= ?"); params.add(f.value()); }
            case LT -> { sql.append(col).append(" < ?"); params.add(f.value()); }
            case LTE -> { sql.append(col).append(" <= ?"); params.add(f.value()); }
            case LIKE -> { sql.append(col).append(" LIKE ?"); params.add(f.value()); }
            case CONTAINS -> { sql.append(col).append(" LIKE ?"); params.add("%" + f.value() + "%"); }
            case IN -> {
                List<Object> vals = toList(f.value());
                if (vals.isEmpty()) {
                    sql.append("1 = 0"); // 空 IN 永不匹配
                } else {
                    sql.append(col).append(" IN (");
                    for (int k = 0; k < vals.size(); k++) {
                        if (k > 0) sql.append(", ");
                        sql.append("?");
                        params.add(vals.get(k));
                    }
                    sql.append(")");
                }
            }
            case IS_NULL -> sql.append(col).append(" IS NULL");
            case IS_NOT_NULL -> sql.append(col).append(" IS NOT NULL");
        }
    }

    private static List<Object> toList(Object value) {
        if (value == null) return List.of();
        if (value instanceof Collection<?> c) return new ArrayList<>(c);
        if (value.getClass().isArray()) {
            int n = java.lang.reflect.Array.getLength(value);
            List<Object> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) out.add(java.lang.reflect.Array.get(value, i));
            return out;
        }
        return List.of(value);
    }

    @SuppressWarnings("unchecked")
    private void foldRow(Map<Object, Map<String, Object>> rootRows, ResultSet rs,
                         ResultSetMetaData md, List<ColumnRef> projection, List<JoinNode> joinNodes) throws SQLException {
        Map<String, Map<String, Object>> rowByPath = new LinkedHashMap<>();
        for (int i = 0; i < projection.size(); i++) {
            ColumnRef ref = projection.get(i);
            Object value = JdbcValueCoercer.coerce(rs.getObject(i + 1), ref.field());
            rowByPath.computeIfAbsent(ref.path, k -> new LinkedHashMap<>())
                .put(ref.column, value);
        }

        Map<String, Object> rootData = rowByPath.get("");
        if (rootData == null) return;
        Object rootId = rootData.get("id");
        if (rootId == null) return;
        Map<String, Object> existing = rootRows.computeIfAbsent(rootId, k -> new LinkedHashMap<>(rootData));

        for (JoinNode j : joinNodes) {
            Map<String, Object> joined = rowByPath.get(j.path);
            if (joined == null) continue;
            if (joined.values().stream().allMatch(Objects::isNull)) continue;

            Map<String, Object> parent = locate(existing, j.parentPath);
            if (parent == null) continue;

            if (j.edge.kind() == RelationDef.Kind.HAS_MANY) {
                List<Map<String, Object>> list = (List<Map<String, Object>>) parent
                    .computeIfAbsent(j.edge.relationName(), k -> new ArrayList<Map<String, Object>>());
                Object childId = joined.get("id");
                boolean alreadyIn = childId != null && list.stream().anyMatch(m -> Objects.equals(m.get("id"), childId));
                if (!alreadyIn) list.add(new LinkedHashMap<>(joined));
            } else {
                parent.putIfAbsent(j.edge.relationName(), new LinkedHashMap<>(joined));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> locate(Map<String, Object> root, String path) {
        if (path.isEmpty()) return root;
        String[] segs = path.split("\\.");
        Map<String, Object> cursor = root;
        for (int i = 0; i < segs.length; i++) {
            Object next = cursor.get(segs[i]);
            if (next instanceof Map<?, ?> m) {
                cursor = (Map<String, Object>) m;
            } else if (next instanceof List<?> list && !list.isEmpty()) {
                cursor = (Map<String, Object>) list.get(list.size() - 1);
            } else {
                return null;
            }
        }
        return cursor;
    }

    private List<JoinNode> planJoins(QueryIntent intent) {
        RelationGraph graph = registry.relationGraph();
        List<JoinNode> result = new ArrayList<>();
        Map<String, String> aliasByPath = new LinkedHashMap<>();
        aliasByPath.put("", "t0");
        int counter = 1;
        for (String include : intent.includes()) {
            List<RelationGraph.Edge> edges = graph.path(intent.entity(), include);
            String parentPath = "";
            for (int i = 0; i < edges.size(); i++) {
                RelationGraph.Edge edge = edges.get(i);
                String currentPath = parentPath.isEmpty() ? edge.relationName()
                    : parentPath + "." + edge.relationName();
                if (!aliasByPath.containsKey(currentPath)) {
                    String alias = "t" + (counter++);
                    aliasByPath.put(currentPath, alias);
                    EntityDef target = registry.require(edge.to());
                    JoinNode node = new JoinNode();
                    node.entity = target;
                    node.alias = alias;
                    node.parentAlias = aliasByPath.get(parentPath);
                    node.path = currentPath;
                    node.parentPath = parentPath;
                    node.edge = edge;
                    result.add(node);
                }
                parentPath = currentPath;
            }
        }
        return result;
    }

    /**
     * 输出列。explicit 非空 = 根实体显式投影（点名列覆盖 readable 限制）；
     * explicit==null = 全部可读列。两种情况都强制包含主键（foldRow 依赖 id）。
     */
    private void appendColumns(List<ColumnRef> projection, StringBuilder sql,
                               EntityDef entity, String alias, String path, Set<String> explicit) {
        int i = 0;
        for (FieldDef f : entity.fields().values()) {
            boolean include;
            if (explicit != null && !explicit.isEmpty()) {
                include = explicit.contains(f.name()) || f.pk();
            } else {
                include = f.readable() || f.pk();
            }
            if (!include) continue;
            if (i++ > 0) sql.append(", ");
            sql.append(alias).append(".").append(f.name())
                .append(" AS ").append(alias).append("_").append(f.name());
            projection.add(new ColumnRef(path, f.name(), f));
        }
    }

    private static final class JoinNode {
        EntityDef entity;
        String alias;
        String parentAlias;
        String path;
        String parentPath;
        RelationGraph.Edge edge;
    }

    private record ColumnRef(String path, String column, FieldDef field) {}
}

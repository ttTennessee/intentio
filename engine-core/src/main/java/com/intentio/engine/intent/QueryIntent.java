package com.intentio.engine.intent;

import java.util.ArrayList;
import java.util.List;

public final class QueryIntent {
    private final String entity;
    private final List<String> includes = new ArrayList<>();
    private final List<Filter> filters = new ArrayList<>();
    private final List<Order> orders = new ArrayList<>();
    private final List<String> select = new ArrayList<>();
    private Integer limit;
    private Integer offset;

    private QueryIntent(String entity) {
        this.entity = entity;
    }

    public static QueryIntent from(String entity) {
        return new QueryIntent(entity);
    }

    public QueryIntent include(String... relations) {
        for (String r : relations) includes.add(r);
        return this;
    }

    /** 等值过滤（兼容旧调用）。 */
    public QueryIntent filter(String field, Object value) {
        filters.add(new Filter(field, FilterOp.EQ, value));
        return this;
    }

    /** 带操作符的过滤。 */
    public QueryIntent filter(String field, FilterOp op, Object value) {
        filters.add(new Filter(field, op, value));
        return this;
    }

    /** 升序排序。 */
    public QueryIntent orderBy(String field) {
        orders.add(new Order(field, true));
        return this;
    }

    public QueryIntent orderBy(String field, boolean asc) {
        orders.add(new Order(field, asc));
        return this;
    }

    public QueryIntent orderByDesc(String field) {
        orders.add(new Order(field, false));
        return this;
    }

    /** 限定根实体输出的列（空 = 全列）。 */
    public QueryIntent select(String... fields) {
        for (String f : fields) select.add(f);
        return this;
    }

    public QueryIntent limit(int limit) { this.limit = limit; return this; }
    public QueryIntent offset(int offset) { this.offset = offset; return this; }

    public String entity() { return entity; }
    public List<String> includes() { return includes; }
    public List<Filter> filters() { return filters; }
    public List<Order> orders() { return orders; }
    public List<String> select() { return select; }
    public Integer limit() { return limit; }
    public Integer offset() { return offset; }
}

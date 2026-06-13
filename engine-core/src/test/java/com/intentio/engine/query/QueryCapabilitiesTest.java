package com.intentio.engine.query;

import com.intentio.engine.intent.FilterOp;
import com.intentio.engine.intent.IntentGroup;
import com.intentio.engine.intent.Op;
import com.intentio.engine.intent.QueryIntent;
import com.intentio.engine.result.IntentResult;
import com.intentio.engine.result.QueryResult;
import com.intentio.engine.support.H2Fixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QueryCapabilitiesTest {

    private H2Fixture fx;

    @BeforeEach
    void setUp() {
        fx = H2Fixture.create();
        insert("alice", 30L);
        insert("bob", 20L);
        insert("carol", null);
        insert("dave", 40L);
    }

    private void insert(String username, Long age) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("username", username);
        if (age != null) fields.put("age", age);
        IntentResult r = fx.engine.execute(IntentGroup.of(Op.insert("Account", fields)));
        assertTrue(r.ok(), () -> "seed failed: " + r.errors());
    }

    private List<String> usernames(QueryResult result) {
        return result.rows().stream().map(m -> String.valueOf(m.get("username"))).toList();
    }

    @Test
    void containsMatchesSubstring() {
        QueryResult r = fx.engine.query(
            QueryIntent.from("Account").filter("username", FilterOp.CONTAINS, "a").orderBy("username"));
        assertEquals(List.of("alice", "carol", "dave"), usernames(r));
    }

    @Test
    void rangeFilters() {
        QueryResult gte = fx.engine.query(
            QueryIntent.from("Account").filter("age", FilterOp.GTE, 30).orderBy("username"));
        assertEquals(List.of("alice", "dave"), usernames(gte));

        QueryResult lt = fx.engine.query(
            QueryIntent.from("Account").filter("age", FilterOp.LT, 30));
        assertEquals(List.of("bob"), usernames(lt)); // carol(age null) 不参与 < 比较
    }

    @Test
    void inFilter() {
        QueryResult r = fx.engine.query(
            QueryIntent.from("Account")
                .filter("username", FilterOp.IN, List.of("alice", "bob"))
                .orderBy("username"));
        assertEquals(List.of("alice", "bob"), usernames(r));
    }

    @Test
    void isNullFilter() {
        QueryResult r = fx.engine.query(
            QueryIntent.from("Account").filter("age", FilterOp.IS_NULL, null));
        assertEquals(List.of("carol"), usernames(r));
    }

    @Test
    void orderByDescending() {
        QueryResult r = fx.engine.query(QueryIntent.from("Account").orderByDesc("username"));
        assertEquals(List.of("dave", "carol", "bob", "alice"), usernames(r));
    }

    @Test
    void projectionLimitsColumnsButKeepsPk() {
        QueryResult r = fx.engine.query(
            QueryIntent.from("Account").select("username").filter("username", "alice"));
        Map<String, Object> row = r.first();
        assertTrue(row.containsKey("id"));        // pk 强制保留
        assertTrue(row.containsKey("username"));
        assertFalse(row.containsKey("age"));
        assertFalse(row.containsKey("status"));
    }

    @Test
    void unknownColumnRejected() {
        assertThrows(RuntimeException.class, () ->
            fx.engine.query(QueryIntent.from("Account").filter("nope", FilterOp.EQ, 1)));
        assertThrows(RuntimeException.class, () ->
            fx.engine.query(QueryIntent.from("Account").orderBy("nope")));
    }
}

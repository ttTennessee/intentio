package com.intentio.engine.execute;

import com.intentio.engine.intent.IntentGroup;
import com.intentio.engine.intent.Op;
import com.intentio.engine.intent.QueryIntent;
import com.intentio.engine.result.IntentResult;
import com.intentio.engine.result.QueryResult;
import com.intentio.engine.support.H2Fixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DeleteEnhancementTest {

    private H2Fixture fx;

    @BeforeEach
    void setUp() {
        fx = H2Fixture.create();
    }

    private IntentResult execute(Op... ops) {
        return fx.engine.execute(IntentGroup.of(ops));
    }

    private long num(Object id) {
        return ((Number) id).longValue();
    }

    private int count(String entity) {
        return fx.engine.query(QueryIntent.from(entity)).size();
    }

    @Test
    void cascadeDeletesTwoLevels() {
        IntentResult r = execute(
            Op.insert("Account", Map.of("username", "alice")).as("acc"),
            Op.insert("Post", Map.of("account_id", "@acc", "title", "p1")).as("post"),
            Op.insert("Comment", Map.of("post_id", "@post", "body", "c1")),
            Op.insert("Comment", Map.of("post_id", "@post", "body", "c2")));
        assertTrue(r.ok(), () -> "errors: " + r.errors());
        long accId = num(r.generatedId("acc"));

        IntentResult del = execute(Op.delete("Account", accId));
        assertTrue(del.ok(), () -> "errors: " + del.errors());
        assertEquals(0, count("Account"));
        assertEquals(0, count("Post"));
        assertEquals(0, count("Comment"));
    }

    @Test
    void setNullOnDelete() {
        IntentResult r = execute(
            Op.insert("Account", Map.of("username", "alice")).as("acc"),
            Op.insert("Audit", Map.of("account_id", "@acc", "action", "login")));
        assertTrue(r.ok(), () -> "errors: " + r.errors());
        long accId = num(r.generatedId("acc"));

        IntentResult del = execute(Op.delete("Account", accId));
        assertTrue(del.ok(), () -> "errors: " + del.errors());
        assertEquals(0, count("Account"));

        QueryResult audits = fx.engine.query(QueryIntent.from("Audit"));
        assertEquals(1, audits.size());
        assertNull(audits.first().get("account_id"), "子行 fk 应被置空");
    }

    @Test
    void restrictBlocksDelete() {
        IntentResult r = execute(
            Op.insert("Account", Map.of("username", "alice")).as("acc"),
            Op.insert("Token", Map.of("account_id", "@acc", "tok_value", "t1")));
        assertTrue(r.ok(), () -> "errors: " + r.errors());
        long accId = num(r.generatedId("acc"));

        IntentResult del = execute(Op.delete("Account", accId));
        assertFalse(del.ok());
        assertTrue(del.errors().stream().anyMatch(e -> "on_delete".equals(e.rule())),
            () -> "expected on_delete error, got: " + del.errors());
        assertEquals(1, count("Account"), "RESTRICT 应整体回滚");
        assertEquals(1, count("Token"));
    }

    @Test
    void deleteWhereBulkRemovesMatchingRows() {
        IntentResult r = execute(
            Op.insert("Account", Map.of("username", "alice")).as("acc"),
            Op.insert("Post", Map.of("account_id", "@acc", "title", "p1")).as("p1"),
            Op.insert("Post", Map.of("account_id", "@acc", "title", "p2")).as("p2"),
            Op.insert("Comment", Map.of("post_id", "@p1", "body", "c1")),
            Op.insert("Comment", Map.of("post_id", "@p1", "body", "c2")),
            Op.insert("Comment", Map.of("post_id", "@p2", "body", "c3")));
        assertTrue(r.ok(), () -> "errors: " + r.errors());
        long p1 = num(r.generatedId("p1"));

        IntentResult del = execute(Op.deleteWhere("Comment", Map.of("post_id", p1)));
        assertTrue(del.ok(), () -> "errors: " + del.errors());
        assertEquals(1, count("Comment"), "仅 p2 的评论 c3 保留");
    }
}

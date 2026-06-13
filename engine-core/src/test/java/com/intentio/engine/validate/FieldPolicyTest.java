package com.intentio.engine.validate;

import com.intentio.engine.intent.IntentGroup;
import com.intentio.engine.intent.Op;
import com.intentio.engine.intent.QueryIntent;
import com.intentio.engine.result.IntentError;
import com.intentio.engine.result.IntentResult;
import com.intentio.engine.result.QueryResult;
import com.intentio.engine.support.H2Fixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FieldPolicyTest {

    private H2Fixture fx;

    @BeforeEach
    void setUp() {
        fx = H2Fixture.create();
    }

    @Test
    void nonWritableFieldRejected() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("username", "alice");
        fields.put("secret", "leak");
        IntentResult r = fx.engine.execute(IntentGroup.of(Op.insert("Account", fields)));
        assertFalse(r.ok());
        assertTrue(r.errors().stream().anyMatch(e -> "secret".equals(e.field())),
            () -> "expected writable error on secret, got: " + r.errors());
    }

    @Test
    void trustedOpBypassesWritable() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("username", "alice");
        fields.put("secret", "topsecret");
        IntentResult r = fx.engine.execute(IntentGroup.of(
            Op.insert("Account", fields).trusted()));
        assertTrue(r.ok(), () -> "errors: " + r.errors());

        // 默认查询不返回 secret（readable:false）
        QueryResult def = fx.engine.query(QueryIntent.from("Account"));
        assertFalse(def.first().containsKey("secret"));

        // 显式 select 可取回
        QueryResult explicit = fx.engine.query(
            QueryIntent.from("Account").select("id", "username", "secret"));
        assertEquals("topsecret", explicit.first().get("secret"));
    }

    @Test
    void hiddenFieldNotLeakedThroughInclude() {
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("username", "alice");
        account.put("secret", "topsecret");
        IntentResult ins = fx.engine.execute(IntentGroup.of(
            Op.insert("Account", account).as("acc").trusted(),
            Op.insert("Post", Map.of("account_id", "@acc", "title", "hello")).as("post")));
        assertTrue(ins.ok(), () -> "errors: " + ins.errors());

        QueryResult posts = fx.engine.query(QueryIntent.from("Post").include("account"));
        @SuppressWarnings("unchecked")
        Map<String, Object> nestedAccount = (Map<String, Object>) posts.first().get("account");
        assertNotNull(nestedAccount);
        assertEquals("alice", nestedAccount.get("username"));
        assertFalse(nestedAccount.containsKey("secret"), "include 不应泄露隐藏列");
    }
}

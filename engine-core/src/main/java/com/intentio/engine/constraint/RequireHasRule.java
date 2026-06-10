package com.intentio.engine.constraint;

import com.intentio.engine.intent.Op;
import com.intentio.engine.intent.OpType;
import com.intentio.engine.result.IntentError;
import com.intentio.engine.schema.Rule;
import com.intentio.engine.schema.SchemaRegistry;
import com.intentio.engine.vm.VirtualMachine;

import java.sql.Connection;
import java.util.Map;
import java.util.Optional;

public final class RequireHasRule {
    private final VirtualMachine vm;

    public RequireHasRule(SchemaRegistry registry, VirtualMachine vm) {
        this.vm = vm;
    }

    public Optional<IntentError> check(Op op, Rule rule, Map<String, Object> resolvedFields, Connection conn) {
        if (op.type() != OpType.INSERT && op.type() != OpType.UPDATE) return Optional.empty();
        String relation = (String) rule.param("relation");
        int min = rule.param("min") instanceof Number n ? n.intValue() : 1;

        Object parentId = vm.get(op.name()) != null ? vm.get(op.name()).generatedId() : null;
        if (parentId == null) parentId = resolvedFields.get("id");
        if (parentId == null) {
            return Optional.of(IntentError.of(op.name(), op.entity(), "require_has",
                "parent id missing for require_has"));
        }

        int dbCount = vm.countRelatedDb(conn, op.entity(), parentId, relation);
        if (dbCount < min) {
            String msg = op.entity() + " requires at least " + min + " " + relation
                + " but found " + dbCount;
            return Optional.of(IntentError.of(op.name(), op.entity(), "require_has",
                rule.message() != null ? rule.message() : msg));
        }
        return Optional.empty();
    }
}

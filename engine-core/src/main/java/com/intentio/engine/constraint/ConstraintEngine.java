package com.intentio.engine.constraint;

import com.intentio.engine.intent.Op;
import com.intentio.engine.intent.OpType;
import com.intentio.engine.result.IntentError;
import com.intentio.engine.schema.EntityDef;
import com.intentio.engine.schema.Rule;
import com.intentio.engine.schema.SchemaRegistry;
import com.intentio.engine.vm.VirtualMachine;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ConstraintEngine {
    private final SchemaRegistry registry;
    private final VirtualMachine vm;
    private final RequireHasRule requireHas;
    private final StockCheckRule stockCheck;
    private final RefCheckRule refCheck;

    public ConstraintEngine(SchemaRegistry registry, VirtualMachine vm) {
        this.registry = registry;
        this.vm = vm;
        this.requireHas = new RequireHasRule(registry, vm);
        this.stockCheck = new StockCheckRule(registry);
        this.refCheck = new RefCheckRule(registry);
    }

    public Optional<IntentError> checkPre(Op op, Map<String, Object> resolvedFields, Connection conn) {
        EntityDef entity = registry.require(op.entity());
        List<Rule> rules = switch (op.type()) {
            case INSERT -> entity.integrity().onCreate();
            case UPDATE -> entity.integrity().onUpdate();
            case DELETE -> entity.integrity().onDelete();
        };
        for (Rule rule : rules) {
            Optional<IntentError> error = switch (rule.type()) {
                case STOCK_CHECK -> stockCheck.check(op, rule, resolvedFields, conn);
                case REF_CHECK -> refCheck.check(op, rule, resolvedFields, conn);
                case REQUIRE_HAS, EXPRESSION -> Optional.empty();
            };
            if (error.isPresent()) return error;
        }
        if (op.type() == OpType.INSERT) {
            Optional<IntentError> err = checkBelongsToRefs(op, resolvedFields, conn);
            if (err.isPresent()) return err;
        }
        return Optional.empty();
    }

    public Optional<IntentError> checkPost(Op op, Map<String, Object> resolvedFields, Connection conn) {
        EntityDef entity = registry.require(op.entity());
        List<Rule> rules = switch (op.type()) {
            case INSERT -> entity.integrity().onCreate();
            case UPDATE -> entity.integrity().onUpdate();
            case DELETE -> entity.integrity().onDelete();
        };
        for (Rule rule : rules) {
            if (rule.type() != Rule.Type.REQUIRE_HAS) continue;
            Optional<IntentError> error = requireHas.check(op, rule, resolvedFields, conn);
            if (error.isPresent()) return error;
        }
        return Optional.empty();
    }

    private Optional<IntentError> checkBelongsToRefs(Op op, Map<String, Object> fields, Connection conn) {
        EntityDef entity = registry.require(op.entity());
        for (var rel : entity.relations().values()) {
            if (rel.kind() != com.intentio.engine.schema.RelationDef.Kind.BELONGS_TO) continue;
            Object fkValue = fields.get(rel.fk());
            if (fkValue == null) continue;
            if (vm.has(extractOpName(op, rel.fk()))) continue;
            boolean exists = refCheck.existsInDb(conn, rel.targetEntity(), fkValue);
            if (!exists) {
                return Optional.of(IntentError.of(op.name(), op.entity(), "ref_check",
                    "Referenced " + rel.targetEntity() + "(id=" + fkValue + ") does not exist"));
            }
        }
        return Optional.empty();
    }

    private String extractOpName(Op op, String fk) {
        Object raw = op.fields().get(fk);
        if (raw instanceof String s && s.startsWith("@")) {
            return com.intentio.engine.intent.ReferenceParser.parse(s).opName();
        }
        return "";
    }
}

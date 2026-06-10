package com.intentio.engine.constraint;

import com.intentio.engine.intent.Op;
import com.intentio.engine.result.IntentError;
import com.intentio.engine.schema.EntityDef;
import com.intentio.engine.schema.Rule;
import com.intentio.engine.schema.SchemaRegistry;
import com.intentio.engine.validate.Validator;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StockCheckRule {

    private static final Pattern RULE = Pattern.compile(
        "^\\s*stock\\.([A-Za-z_]\\w*)\\s*(>=|<=|>|<|==|!=)\\s*item\\.([A-Za-z_]\\w*)\\s*$");

    private final SchemaRegistry registry;

    public StockCheckRule(SchemaRegistry registry) {
        this.registry = registry;
    }

    public Optional<IntentError> check(Op op, Rule rule, Map<String, Object> resolvedFields, Connection conn) {
        String via = (String) rule.param("via");
        String fk = (String) rule.param("fk");
        String expr = (String) rule.param("rule");
        Matcher m = RULE.matcher(expr == null ? "" : expr);
        if (!m.matches()) {
            throw new IllegalArgumentException("Unsupported stock_check expression: " + expr);
        }
        String stockCol = m.group(1);
        String op2 = m.group(2);
        String itemCol = m.group(3);

        Object fkValue = resolvedFields.get(fk);
        if (fkValue == null) return Optional.empty();

        EntityDef stockEntity = registry.require(via);
        String sql = "SELECT " + stockCol + " FROM " + stockEntity.table() + " WHERE " + fk + " = ?";
        BigDecimal stockValue = null;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, fkValue);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    stockValue = rs.getBigDecimal(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("stock_check db read failed", e);
        }
        if (stockValue == null) {
            return Optional.of(IntentError.of(op.name(), op.entity(), "stock_check",
                rule.message() != null ? rule.message() : "stock record not found"));
        }

        BigDecimal itemValue = Validator.toBigDecimal(resolvedFields.get(itemCol));
        if (itemValue == null) {
            return Optional.of(IntentError.of(op.name(), op.entity(), "stock_check",
                "missing item." + itemCol));
        }
        int cmp = stockValue.compareTo(itemValue);
        boolean ok = switch (op2) {
            case ">=" -> cmp >= 0;
            case ">" -> cmp > 0;
            case "<=" -> cmp <= 0;
            case "<" -> cmp < 0;
            case "==" -> cmp == 0;
            case "!=" -> cmp != 0;
            default -> false;
        };
        if (!ok) {
            String msg = rule.message() != null ? rule.message() : "stock check failed";
            msg = msg + " (当前库存 " + stockValue.toPlainString()
                + ", 需要 " + itemValue.toPlainString() + ")";
            return Optional.of(IntentError.of(op.name(), op.entity(), "stock_check", msg));
        }
        return Optional.empty();
    }
}

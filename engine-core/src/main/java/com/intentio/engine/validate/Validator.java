package com.intentio.engine.validate;

import com.intentio.engine.intent.IntentGroup;
import com.intentio.engine.intent.Op;
import com.intentio.engine.intent.OpType;
import com.intentio.engine.intent.ReferenceParser;
import com.intentio.engine.result.IntentError;
import com.intentio.engine.schema.EntityDef;
import com.intentio.engine.schema.FieldDef;
import com.intentio.engine.schema.SchemaRegistry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Validator {
    private final SchemaRegistry registry;

    public Validator(SchemaRegistry registry) {
        this.registry = registry;
    }

    public List<IntentError> validate(IntentGroup group) {
        List<IntentError> errors = new ArrayList<>();
        Set<String> namesSoFar = new HashSet<>();
        for (Op op : group.ops()) {
            validateOne(op, namesSoFar, errors);
            namesSoFar.add(op.name());
        }
        return errors;
    }

    private void validateOne(Op op, Set<String> earlierOpNames, List<IntentError> errors) {
        EntityDef entity = registry.find(op.entity()).orElse(null);
        if (entity == null) {
            errors.add(IntentError.of(op.name(), op.entity(), "entity", "Unknown entity: " + op.entity()));
            return;
        }

        if (op.type() == OpType.DELETE) {
            if (op.targetId() == null && op.conditions().isEmpty()) {
                errors.add(IntentError.field(op.name(), op.entity(), "id",
                    "DELETE requires target id or conditions"));
            }
            return;
        }

        for (Map.Entry<String, Object> e : op.fields().entrySet()) {
            String fieldName = e.getKey();
            Object value = e.getValue();
            FieldDef def = entity.fields().get(fieldName);
            if (def == null) {
                errors.add(IntentError.field(op.name(), op.entity(), fieldName,
                    "Field not defined in schema: " + fieldName));
                continue;
            }
            if (!def.writable() && !op.isTrusted()) {
                errors.add(IntentError.field(op.name(), op.entity(), fieldName,
                    "Field is not writable: " + fieldName));
                continue;
            }
            if (ReferenceParser.isReference(value)) {
                var ref = ReferenceParser.parse((String) value);
                if (!earlierOpNames.contains(ref.opName())) {
                    errors.add(IntentError.field(op.name(), op.entity(), fieldName,
                        "Reference '@" + ref.opName() + "' does not match any earlier op"));
                }
                continue;
            }
            validateValue(op, def, value, errors);
        }

        if (op.type() == OpType.INSERT) {
            for (FieldDef def : entity.fields().values()) {
                if (def.pk() && def.autoIncrement()) continue;
                if (!def.required()) continue;
                if (def.defaultValue() != null) continue;
                if (!op.fields().containsKey(def.name())) {
                    errors.add(IntentError.field(op.name(), op.entity(), def.name(),
                        "Required field missing: " + def.name()));
                }
            }
        }
    }

    private void validateValue(Op op, FieldDef def, Object value, List<IntentError> errors) {
        if (value == null) {
            if (def.required()) {
                errors.add(IntentError.field(op.name(), op.entity(), def.name(),
                    "Required field is null: " + def.name()));
            }
            return;
        }
        switch (def.type()) {
            case LONG -> {
                if (!(value instanceof Number) && !isLongString(value)) {
                    errors.add(IntentError.field(op.name(), op.entity(), def.name(),
                        "Expected long, got: " + value));
                }
            }
            case STRING -> {
                String s = value.toString();
                if (def.length() != null && s.length() > def.length()) {
                    errors.add(IntentError.field(op.name(), op.entity(), def.name(),
                        "String length " + s.length() + " exceeds max " + def.length()));
                }
            }
            case DECIMAL -> {
                BigDecimal bd = toBigDecimal(value);
                if (bd == null) {
                    errors.add(IntentError.field(op.name(), op.entity(), def.name(),
                        "Expected decimal, got: " + value));
                } else if (def.precision() != null && def.scale() != null) {
                    int integerDigits = bd.precision() - bd.scale();
                    int maxIntegerDigits = def.precision() - def.scale();
                    if (integerDigits > maxIntegerDigits) {
                        errors.add(IntentError.field(op.name(), op.entity(), def.name(),
                            "Decimal exceeds precision " + def.precision() + "/" + def.scale()));
                    }
                }
            }
            case DATE -> {
                if (value instanceof LocalDate) break;
                if (value instanceof java.util.Date) break;
                try {
                    LocalDate.parse(value.toString());
                } catch (DateTimeParseException ex) {
                    errors.add(IntentError.field(op.name(), op.entity(), def.name(),
                        "Invalid date: " + value));
                }
            }
            case DATETIME -> {
                if (value instanceof LocalDateTime) break;
                try {
                    LocalDateTime.parse(value.toString());
                } catch (DateTimeParseException ex) {
                    errors.add(IntentError.field(op.name(), op.entity(), def.name(),
                        "Invalid datetime: " + value));
                }
            }
            case BOOLEAN -> {
                if (!(value instanceof Boolean)
                    && !"true".equalsIgnoreCase(value.toString())
                    && !"false".equalsIgnoreCase(value.toString())) {
                    errors.add(IntentError.field(op.name(), op.entity(), def.name(),
                        "Expected boolean, got: " + value));
                }
            }
            case ENUM -> {
                String s = value.toString();
                if (!def.enumValues().contains(s)) {
                    errors.add(IntentError.field(op.name(), op.entity(), def.name(),
                        "Value '" + s + "' not in enum " + def.enumValues()));
                }
            }
        }
    }

    private boolean isLongString(Object v) {
        try {
            Long.parseLong(v.toString());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static BigDecimal toBigDecimal(Object v) {
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return new BigDecimal(n.toString());
        try {
            return new BigDecimal(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

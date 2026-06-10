package com.intentio.engine.vm;

import com.intentio.engine.intent.Op;
import com.intentio.engine.intent.ReferenceParser;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ReferenceResolver {

    private final VirtualMachine vm;

    public ReferenceResolver(VirtualMachine vm) {
        this.vm = vm;
    }

    public Map<String, Object> resolve(Op op) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : op.fields().entrySet()) {
            resolved.put(e.getKey(), resolveValue(e.getValue()));
        }
        return resolved;
    }

    public Object resolveValue(Object value) {
        if (!ReferenceParser.isReference(value)) return value;
        var ref = ReferenceParser.parse((String) value);
        PendingRow row = vm.get(ref.opName());
        if (row == null) {
            throw new IllegalStateException("Unresolved reference: @" + ref.opName());
        }
        Object resolved = row.value(ref.field());
        if (resolved == null) {
            throw new IllegalStateException(
                "Reference @" + ref.opName() + "." + ref.field() + " has no value yet");
        }
        return resolved;
    }
}

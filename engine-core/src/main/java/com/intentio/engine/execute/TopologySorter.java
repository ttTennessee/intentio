package com.intentio.engine.execute;

import com.intentio.engine.intent.IntentGroup;
import com.intentio.engine.intent.Op;
import com.intentio.engine.intent.ReferenceParser;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TopologySorter {

    private TopologySorter() {}

    public static List<Op> sort(IntentGroup group) {
        List<Op> ops = group.ops();
        Map<String, Op> byName = new LinkedHashMap<>();
        for (Op op : ops) byName.put(op.name(), op);

        Map<String, Set<String>> dependsOn = new HashMap<>();
        Map<String, Set<String>> dependedBy = new HashMap<>();
        for (Op op : ops) {
            dependsOn.put(op.name(), new HashSet<>());
            dependedBy.put(op.name(), new HashSet<>());
        }
        for (Op op : ops) {
            for (Object v : op.fields().values()) {
                if (ReferenceParser.isReference(v)) {
                    var ref = ReferenceParser.parse((String) v);
                    if (byName.containsKey(ref.opName()) && !ref.opName().equals(op.name())) {
                        dependsOn.get(op.name()).add(ref.opName());
                        dependedBy.get(ref.opName()).add(op.name());
                    }
                }
            }
        }

        Deque<Op> queue = new ArrayDeque<>();
        for (Op op : ops) {
            if (dependsOn.get(op.name()).isEmpty()) queue.add(op);
        }

        List<Op> result = new ArrayList<>(ops.size());
        while (!queue.isEmpty()) {
            Op cur = queue.removeFirst();
            result.add(cur);
            for (String dependent : new ArrayList<>(dependedBy.get(cur.name()))) {
                Set<String> deps = dependsOn.get(dependent);
                deps.remove(cur.name());
                if (deps.isEmpty()) {
                    queue.addLast(byName.get(dependent));
                }
            }
        }

        if (result.size() != ops.size()) {
            throw new IllegalStateException("Cycle detected in intent group references");
        }
        return result;
    }
}

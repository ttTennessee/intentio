package com.intentio.engine.intent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class IntentGroup {
    private final List<Op> ops;

    private IntentGroup(List<Op> ops) {
        this.ops = ops;
    }

    public static IntentGroup of(Op... ops) {
        return of(Arrays.asList(ops));
    }

    public static IntentGroup of(List<Op> ops) {
        List<Op> copy = new ArrayList<>(ops);
        for (int i = 0; i < copy.size(); i++) {
            copy.get(i).assignDefaultName(i);
        }
        return new IntentGroup(copy);
    }

    public List<Op> ops() { return ops; }
}

package com.intentio.engine.intent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public record IntentGroup(List<Op> ops) {

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
}

package io.github.gromoff97.awium.conditions.internal;

import io.github.gromoff97.awium.AwaitCondition.SelectedCondition;
import java.util.Map;

import static io.github.gromoff97.awium.conditions.internal.SingleElementCondition.single;

public final class SingleEntryCondition extends SelectedCondition<Map<?, ?>> {
    public SingleEntryCondition() {
        super("map has a single entry", null, actual -> single("map", actual == null ? null : actual.entrySet()));
    }

}

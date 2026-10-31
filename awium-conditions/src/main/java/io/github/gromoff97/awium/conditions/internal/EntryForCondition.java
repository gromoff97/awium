package io.github.gromoff97.awium.conditions.internal;

import io.github.gromoff97.awium.AwaitCondition.SelectedCondition;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.Map.Entry;
import java.util.Map;

import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.util.Objects.deepEquals;

public final class EntryForCondition<KEY> extends SelectedCondition<Map<? super KEY, ?>> {
    public EntryForCondition(KEY key) {
        super("map contains the expected key", new Reference<>("Key", key), actual -> findEntry(actual, key));
    }

    static <KEY, VALUE> ConditionResult<Entry<KEY, VALUE>> findEntry(Map<KEY, VALUE> actual, Object key) {
        if (actual == null) return unsatisfied("map was null");
        for (Entry<KEY, VALUE> entry : actual.entrySet()) {
            if (deepEquals(entry.getKey(), key)) {
                return satisfied(entry);
            }
        }
        return unsatisfied("map did not contain the expected key");
    }

}

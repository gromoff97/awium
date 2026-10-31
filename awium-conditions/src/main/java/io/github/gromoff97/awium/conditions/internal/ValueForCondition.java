package io.github.gromoff97.awium.conditions.internal;

import io.github.gromoff97.awium.AwaitCondition.MapValueCondition;
import java.util.Map.Entry;
import java.util.Map;

import static io.github.gromoff97.awium.conditions.internal.EntryForCondition.findEntry;

public final class ValueForCondition<KEY> extends MapValueCondition<Map<? super KEY, ?>> {
    public ValueForCondition(KEY key) {
        super("map contains the expected key", new Reference<>("Key", key), actual -> findEntry(actual, key).mapValue(Entry::getValue));
    }

}

package io.github.gromoff97.awium.conditions.internal;

import io.github.gromoff97.awium.AwaitCondition.SelectedCondition;
import java.util.Map;

import static io.github.gromoff97.awium.conditions.internal.MapEntries.findEntry;

public final class EntryForCondition<KEY> extends SelectedCondition<Map<? super KEY, ?>> {
    public EntryForCondition(KEY key) {
        super("map contains the expected key", new Reference<>("Key", key), actual -> findEntry(actual, key));
    }
}

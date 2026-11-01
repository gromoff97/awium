package io.github.gromoff97.awium.conditions.map;

import io.github.gromoff97.awium.AwaitCondition.MapValueCondition;
import java.util.Map.Entry;
import java.util.Map;

import static io.github.gromoff97.awium.conditions.map.MapEntries.findEntry;

final class ValueForCondition<KEY> extends MapValueCondition<Map<? super KEY, ?>> {
    ValueForCondition(KEY key) {
        super("map contains the expected key", new Reference<>("Key", key), actual -> findEntry(actual, key).mapValue(Entry::getValue));
    }

}

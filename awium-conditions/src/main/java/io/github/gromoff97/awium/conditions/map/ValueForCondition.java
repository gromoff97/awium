package io.github.gromoff97.awium.conditions.map;

import io.github.gromoff97.awium.AwaitCondition.MapValueCondition;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.List;
import java.util.Map.Entry;
import java.util.Map;

import static io.github.gromoff97.awium.conditions.map.MapEntries.findEntry;

record ValueForCondition<KEY>(KEY key) implements MapValueCondition<Map<? super KEY, ?>> {
    @Override public String description() { return "map contains the expected key"; }
    @Override public List<Reference<?>> references() { return List.of(new Reference<>("Key", key)); }
    @Override public AttemptResult<?> probe(Map<? super KEY, ?> actual) { return findEntry(actual, key).mapValue(Entry::getValue); }
}

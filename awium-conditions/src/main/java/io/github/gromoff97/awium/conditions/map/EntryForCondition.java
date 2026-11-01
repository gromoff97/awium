package io.github.gromoff97.awium.conditions.map;

import io.github.gromoff97.awium.AwaitCondition.SelectedCondition;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.List;
import java.util.Map;

import static io.github.gromoff97.awium.conditions.map.MapEntries.findEntry;

record EntryForCondition<KEY>(KEY key) implements SelectedCondition<Map<? super KEY, ?>> {
    @Override public String description() { return "map contains the expected key"; }
    @Override public List<Reference<?>> references() { return List.of(new Reference<>("Key", key)); }
    @Override public AttemptResult<?> probe(Map<? super KEY, ?> actual) { return findEntry(actual, key); }
}

package io.github.gromoff97.awium.conditions.map;

import io.github.gromoff97.awium.function.checked.CheckedBiPredicate;
import io.github.gromoff97.awium.function.checked.CheckedPredicate;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.List;
import java.util.Map.Entry;
import java.util.Map;

import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.util.Objects.deepEquals;
import static java.util.Objects.requireNonNull;

final class MapEntries {
    private MapEntries() {}

    static <KEY, VALUE> ConditionResult<Entry<KEY, VALUE>> findEntry(Map<KEY, VALUE> actual, Object key) {
        if (actual == null) return unsatisfied("map was null");
        for (Entry<KEY, VALUE> entry : actual.entrySet()) {
            if (deepEquals(entry.getKey(), key)) {
                return satisfied(entry);
            }
        }
        return unsatisfied("map did not contain the expected key");
    }

    static <KEY, VALUE> CheckedPredicate<Entry<? extends KEY, ? extends VALUE>> entryPredicate(
            CheckedBiPredicate<? super KEY, ? super VALUE> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return entry -> predicate.test(entry.getKey(), entry.getValue());
    }

    static List<? extends Entry<?, ?>> snapshotEntries(Map<?, ?> values, String name) {
        return requireNonNull(values, name + " must not be null")
                .entrySet().stream().map(SimpleImmutableEntry::new).toList();
    }

    static boolean entryMatches(Entry<?, ?> actual, Entry<?, ?> expected) {
        return deepEquals(actual.getKey(), expected.getKey()) && deepEquals(actual.getValue(), expected.getValue());
    }
}

package io.github.gromoff97.awium.conditions.internal;

import io.github.gromoff97.awium.conditions.function.CheckedBiPredicate;
import io.github.gromoff97.awium.conditions.function.CheckedPredicate;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.List;
import java.util.Map.Entry;
import java.util.Map;

import static java.util.Objects.deepEquals;
import static java.util.Objects.requireNonNull;

public final class MapEntries {
    private MapEntries() {}

    public static <KEY, VALUE> CheckedPredicate<Entry<? extends KEY, ? extends VALUE>> entryPredicate(
            CheckedBiPredicate<? super KEY, ? super VALUE> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return entry -> predicate.test(entry.getKey(), entry.getValue());
    }

    public static List<? extends Entry<?, ?>> snapshotEntries(Map<?, ?> values, String name) {
        return requireNonNull(values, name + " must not be null")
                .entrySet().stream().map(SimpleImmutableEntry::new).toList();
    }

    public static boolean entryMatches(Entry<?, ?> actual, Entry<?, ?> expected) {
        return deepEquals(actual.getKey(), expected.getKey()) && deepEquals(actual.getValue(), expected.getValue());
    }
}

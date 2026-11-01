package io.github.gromoff97.awium.conditions.map;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.List;
import java.util.Map.Entry;

import static java.util.Arrays.deepHashCode;
import static java.util.Objects.deepEquals;

record DeepEntry(Entry<?, ?> entry) {
    DeepEntry(Object key, Object value) { this(new SimpleImmutableEntry<>(key, value)); }

    @Override public boolean equals(Object other) {
        return other instanceof DeepEntry expected
                && deepEquals(entry.getKey(), expected.entry.getKey())
                && deepEquals(entry.getValue(), expected.entry.getValue());
    }

    @Override public int hashCode() {
        return deepHashCode(new Object[] {entry.getKey(), entry.getValue()});
    }

    static Object reference(Object value) {
        if (value instanceof DeepEntry pair) return pair.entry;
        if (value instanceof List<?> values && values.stream().anyMatch(DeepEntry.class::isInstance)) {
            return values.stream().map(DeepEntry::reference).toList();
        }
        return value;
    }
}

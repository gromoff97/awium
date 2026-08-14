package io.github.gromoff97.awium.conditioning.providers;

import io.github.gromoff97.awium.conditioning.conditions.PreservingCondition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static io.github.gromoff97.awium.conditioning.ValueEquality.equal;
import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.allFound;
import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.anyMatch;
import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.matchCount;
import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.matchingCondition;
import static java.util.Objects.requireNonNull;

public final class MapConditionProvider {

    private MapConditionProvider() {
        throw new AssertionError("Utility class");
    }

    public static <K> PreservingCondition<Map<? super K, ?>> containsKey(K expected) {
        return entryCondition(entry -> equal(entry.getKey(), expected), true, "key");
    }

    public static <K> PreservingCondition<Map<? super K, ?>> doesNotContainKey(K expected) {
        return entryCondition(entry -> equal(entry.getKey(), expected), false, "key");
    }

    public static <V> PreservingCondition<Map<?, ? super V>> containsValue(V expected) {
        return entryCondition(entry -> equal(entry.getValue(), expected), true, "value");
    }

    public static <V> PreservingCondition<Map<?, ? super V>> doesNotContainValue(V expected) {
        return entryCondition(entry -> equal(entry.getValue(), expected), false, "value");
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>> containsEntry(K key, V value) {
        return entryCondition(entry -> entryMatches(entry, key, value), true, "entry");
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>> doesNotContainEntry(K key, V value) {
        return entryCondition(entry -> entryMatches(entry, key, value), false, "entry");
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>> containsAllEntriesOf(Map<? extends K, ? extends V> expected) {
        return membership(expected, true, true, "map contains all expected entries", "map did not contain all expected entries");
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>> doesNotContainAllEntriesOf(Map<? extends K, ? extends V> expected) {
        return membership(expected, true, false, "map does not contain all expected entries", "map contained all expected entries");
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>> containsAnyEntriesOf(Map<? extends K, ? extends V> expected) {
        return membership(expected, false, true, "map contains any expected entry", "map did not contain any expected entry");
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>> containsNoEntriesOf(Map<? extends K, ? extends V> expected) {
        return membership(expected, false, false, "map does not contain any expected entry", "map contained an expected entry");
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>> containsExactlyEntriesOf(Map<? extends K, ? extends V> expected) {
        return exact(expected, true, "map contains exactly the expected entries", "map did not contain exactly the expected entries");
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>> doesNotContainExactlyEntriesOf(Map<? extends K, ? extends V> expected) {
        return exact(expected, false, "map does not contain exactly the expected entries", "map contained exactly the expected entries");
    }

    private static <M extends Map<?, ?>> PreservingCondition<M> entryCondition(Predicate<Map.Entry<?, ?>> matches, boolean positive, String expected) {
        return matchingCondition("map",
                "map " + (positive ? "contains " : "does not contain ") + "expected " + expected,
                "map " + (positive ? "did not contain" : "contained") + " expected " + expected,
                positive, actual -> anyMatch(actual.entrySet(), matches));
    }

    private static <K, V> PreservingCondition<Map<? super K, ? super V>> membership(Map<? extends K, ? extends V> expected, boolean all,
            boolean positive, String description, String mismatch) {
        if (requireNonNull(expected, "expected entries must not be null").isEmpty()) {
            throw new IllegalArgumentException("expected entries must not be empty");
        }
        return matchingCondition("map", description, mismatch, positive, actual -> {
            List<Map.Entry<?, ?>> positions = new ArrayList<>(expected.entrySet());
            return all
                    ? allFound(actual.entrySet(), positions, MapConditionProvider::entryMatches)
                    : anyMatch(actual.entrySet(), actualEntry -> anyMatch(positions,
                            expectedEntry -> entryMatches(actualEntry, expectedEntry)));
        });
    }

    private static <K, V> PreservingCondition<Map<? super K, ? super V>> exact(Map<? extends K, ? extends V> expected, boolean positive,
            String description, String mismatch) {
        requireNonNull(expected, "expected entries must not be null");
        return matchingCondition("map", description, mismatch, positive, actual -> exactContent(actual, expected));
    }

    private static boolean exactContent(Map<?, ?> actual, Map<?, ?> expected) {
        int actualSize = actual.size();
        if (actualSize != expected.size()) {
            return false;
        }
        if (actualSize == 0) {
            return true;
        }

        List<Map.Entry<?, ?>> remaining = new ArrayList<>(expected.entrySet());
        int matched = matchCount(actual.entrySet().iterator(), remaining, MapConditionProvider::entryMatches);
        return matched == actualSize && remaining.isEmpty();
    }

    private static boolean entryMatches(Map.Entry<?, ?> actual, Map.Entry<?, ?> expected) {
        return equal(actual.getKey(), expected.getKey())
                && equal(actual.getValue(), expected.getValue());
    }

    private static boolean entryMatches(Map.Entry<?, ?> actual, Object expectedKey, Object expectedValue) {
        return equal(actual.getKey(), expectedKey)
                && equal(actual.getValue(), expectedValue);
    }

}

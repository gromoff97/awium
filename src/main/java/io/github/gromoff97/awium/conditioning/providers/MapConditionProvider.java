package io.github.gromoff97.awium.conditioning.providers;

import io.github.gromoff97.awium.conditioning.conditions.PreservingCondition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.anyMatch;
import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.containsAllMatches;
import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.equal;
import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.matchesExactly;
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
        return membership(expected, true, true);
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>> doesNotContainAllEntriesOf(Map<? extends K, ? extends V> expected) {
        return membership(expected, true, false);
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>> containsAnyEntriesOf(Map<? extends K, ? extends V> expected) {
        return membership(expected, false, true);
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>> containsNoEntriesOf(Map<? extends K, ? extends V> expected) {
        return membership(expected, false, false);
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>> containsExactlyEntriesOf(Map<? extends K, ? extends V> expected) {
        return exact(expected, true);
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>> doesNotContainExactlyEntriesOf(Map<? extends K, ? extends V> expected) {
        return exact(expected, false);
    }

    private static <M extends Map<?, ?>> PreservingCondition<M> entryCondition(Predicate<Map.Entry<?, ?>> matches, boolean positive, String expected) {
        return matchingCondition("map",
                "map " + (positive ? "contains " : "does not contain ") + "expected " + expected,
                "map " + (positive ? "did not contain" : "contained") + " expected " + expected,
                positive, actual -> anyMatch(actual.entrySet(), matches));
    }

    private static <K, V> PreservingCondition<Map<? super K, ? super V>> membership(Map<? extends K, ? extends V> expected,
            boolean all, boolean positive) {
        if (requireNonNull(expected, "expected entries must not be null").isEmpty()) {
            throw new IllegalArgumentException("expected entries must not be empty");
        }
        String target = all ? "all expected entries" : "any expected entry";
        String description = "map " + (positive ? "contains " : "does not contain ") + target;
        String mismatch = "map " + (positive ? "did not contain " : "contained ")
                + (positive || all ? target : "an expected entry");
        return matchingCondition("map", description, mismatch, positive, actual -> {
            List<Map.Entry<?, ?>> positions = new ArrayList<>(expected.entrySet());
            return all
                    ? containsAllMatches(actual.entrySet(), positions, MapConditionProvider::entryMatches)
                    : anyMatch(actual.entrySet(), actualEntry -> anyMatch(positions,
                            expectedEntry -> entryMatches(actualEntry, expectedEntry)));
        });
    }

    private static <K, V> PreservingCondition<Map<? super K, ? super V>> exact(Map<? extends K, ? extends V> expected, boolean positive) {
        requireNonNull(expected, "expected entries must not be null");
        String description = "map " + (positive ? "contains " : "does not contain ") + "exactly the expected entries";
        String mismatch = "map " + (positive ? "did not contain " : "contained ") + "exactly the expected entries";
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
        return matchesExactly(actual.entrySet().iterator(), remaining, MapConditionProvider::entryMatches);
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

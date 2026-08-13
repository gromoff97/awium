package io.github.gromoff97.awium.conditioning.providers;

import io.github.gromoff97.awium.conditioning.conditions.PreservingCondition;
import io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.unsatisfied;
import static io.github.gromoff97.awium.conditioning.ValueEquality.equal;
import static java.util.Objects.requireNonNull;

public final class MapConditionProvider {

    private MapConditionProvider() {
        throw new AssertionError("Utility class");
    }

    public static <K> PreservingCondition<Map<? super K, ?>> containsKey(
            K expected) {
        return entryCondition(entry -> equal(entry.getKey(), expected), true,
                "key");
    }

    public static <K> PreservingCondition<Map<? super K, ?>> doesNotContainKey(
            K expected) {
        return entryCondition(entry -> equal(entry.getKey(), expected), false,
                "key");
    }

    public static <V> PreservingCondition<Map<?, ? super V>> containsValue(
            V expected) {
        return entryCondition(entry -> equal(entry.getValue(), expected), true,
                "value");
    }

    public static <V> PreservingCondition<Map<?, ? super V>> doesNotContainValue(
            V expected) {
        return entryCondition(entry -> equal(entry.getValue(), expected), false,
                "value");
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>> containsEntry(
            K key, V value) {
        return entryCondition(entry -> entryMatches(entry, key, value), true,
                "entry");
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>>
            doesNotContainEntry(K key, V value) {
        return entryCondition(entry -> entryMatches(entry, key, value), false,
                "entry");
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>>
            containsAllEntriesOf(Map<? extends K, ? extends V> expected) {
        return membership(validate(expected), true, true,
                "map contains all expected entries",
                "map did not contain all expected entries");
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>>
            doesNotContainAllEntriesOf(
                    Map<? extends K, ? extends V> expected) {
        return membership(validate(expected), true, false,
                "map does not contain all expected entries",
                "map contained all expected entries");
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>>
            containsAnyEntriesOf(Map<? extends K, ? extends V> expected) {
        return membership(validate(expected), false, true,
                "map contains any expected entry",
                "map did not contain any expected entry");
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>>
            containsNoEntriesOf(Map<? extends K, ? extends V> expected) {
        return membership(validate(expected), false, false,
                "map does not contain any expected entry",
                "map contained an expected entry");
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>>
            containsExactlyEntriesOf(
                    Map<? extends K, ? extends V> expected) {
        return exact(validateExact(expected), true,
                "map contains exactly the expected entries",
                "map did not contain exactly the expected entries");
    }

    public static <K, V> PreservingCondition<Map<? super K, ? super V>>
            doesNotContainExactlyEntriesOf(
                    Map<? extends K, ? extends V> expected) {
        return exact(validateExact(expected), false,
                "map does not contain exactly the expected entries",
                "map contained exactly the expected entries");
    }

    private static <M extends Map<?, ?>> PreservingCondition<M> entryCondition(
            Predicate<Map.Entry<?, ?>> matches, boolean positive,
            String expected) {
        return condition(actual -> anyMatch(actual, matches), positive,
                "map " + (positive ? "contains " : "does not contain ")
                        + "expected " + expected,
                "map " + (positive ? "did not contain" : "contained")
                        + " expected " + expected);
    }

    private static <M extends Map<?, ?>> PreservingCondition<M> condition(
            Predicate<? super M> matches, boolean positive,
            String description, String mismatch) {
        return PreservingCondition.of(new RuntimeCondition<>(actual -> {
            if (actual == null) {
                return unsatisfied("map was null");
            }
            return matches.test(actual) == positive
                    ? satisfied(actual)
                    : unsatisfied(mismatch);
        }, () -> description, null));
    }

    private static <K, V> PreservingCondition<Map<? super K, ? super V>>
            membership(Map<? extends K, ? extends V> expected, boolean all,
                    boolean positive, String description, String mismatch) {
        return condition(actual -> {
            List<Map.Entry<?, ?>> positions = entries(expected);
            return all
                    ? allFound(actual, positions)
                    : anyMatch(actual, actualEntry -> positions.stream()
                            .anyMatch(expectedEntry -> entryMatches(
                                    actualEntry, expectedEntry)));
        }, positive, description, mismatch);
    }

    private static boolean anyMatch(Map<?, ?> actual,
            Predicate<Map.Entry<?, ?>> matches) {
        for (Map.Entry<?, ?> entry : actual.entrySet()) {
            if (matches.test(entry)) {
                return true;
            }
        }
        return false;
    }

    private static boolean allFound(Map<?, ?> actual,
            List<Map.Entry<?, ?>> expected) {
        List<Map.Entry<?, ?>> remaining = new ArrayList<>(expected);
        for (Map.Entry<?, ?> actualEntry : actual.entrySet()) {
            remaining.removeIf(expectedEntry ->
                    entryMatches(actualEntry, expectedEntry));
            if (remaining.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static <K, V> PreservingCondition<Map<? super K, ? super V>> exact(
            Map<? extends K, ? extends V> expected, boolean positive,
            String description, String mismatch) {
        return condition(actual -> exactContent(actual, expected), positive,
                description, mismatch);
    }

    private static boolean exactContent(Map<?, ?> actual, Map<?, ?> expected) {
        int actualSize = actual.size();
        if (actualSize != expected.size()) {
            return false;
        }
        if (actualSize == 0) {
            return true;
        }

        List<Map.Entry<?, ?>> remaining = entries(expected);
        int matched = 0;
        for (Map.Entry<?, ?> actualEntry : actual.entrySet()) {
            boolean found = false;
            var candidates = remaining.iterator();
            while (candidates.hasNext()) {
                if (entryMatches(actualEntry, candidates.next())) {
                    candidates.remove();
                    matched++;
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return matched == actualSize && remaining.isEmpty();
    }

    private static List<Map.Entry<?, ?>> entries(Map<?, ?> map) {
        return new ArrayList<>(map.entrySet());
    }

    private static boolean entryMatches(Map.Entry<?, ?> actual,
            Map.Entry<?, ?> expected) {
        return equal(actual.getKey(), expected.getKey())
                && equal(
                        actual.getValue(), expected.getValue());
    }

    private static boolean entryMatches(Map.Entry<?, ?> actual,
            Object expectedKey, Object expectedValue) {
        return equal(actual.getKey(), expectedKey)
                && equal(actual.getValue(), expectedValue);
    }

    private static <K, V> Map<? extends K, ? extends V> validate(
            Map<? extends K, ? extends V> expected) {
        if (requireNonNull(expected, "expected entries must not be null").isEmpty()) {
            throw new IllegalArgumentException(
                    "expected entries must not be empty");
        }
        return expected;
    }

    private static <K, V> Map<? extends K, ? extends V> validateExact(
            Map<? extends K, ? extends V> expected) {
        return requireNonNull(
                expected, "expected entries must not be null");
    }
}

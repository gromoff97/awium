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

final class MapConditionProvider {

    static <K> PreservingCondition<Map<? super K, ?>> containsKey(K expected) {
        return condition(actual -> anyMatch(actual, entry ->
                        equal(entry.getKey(), expected)), true,
                "map to contain expected key",
                "map did not contain expected key");
    }

    static <K> PreservingCondition<Map<? super K, ?>> doesNotContainKey(
            K expected) {
        return condition(actual -> anyMatch(actual, entry ->
                        equal(entry.getKey(), expected)), false,
                "map not to contain expected key",
                "map contained expected key");
    }

    static <V> PreservingCondition<Map<?, ? super V>> containsValue(
            V expected) {
        return condition(actual -> anyMatch(actual, entry ->
                        equal(entry.getValue(), expected)), true,
                "map to contain expected value",
                "map did not contain expected value");
    }

    static <V> PreservingCondition<Map<?, ? super V>> doesNotContainValue(
            V expected) {
        return condition(actual -> anyMatch(actual, entry ->
                        equal(entry.getValue(), expected)), false,
                "map not to contain expected value",
                "map contained expected value");
    }

    static <K, V> PreservingCondition<Map<? super K, ? super V>> containsEntry(
            K key, V value) {
        return condition(actual -> anyMatch(actual,
                        entry -> entryMatches(entry, key, value)), true,
                "map to contain expected entry",
                "map did not contain expected entry");
    }

    static <K, V> PreservingCondition<Map<? super K, ? super V>>
            doesNotContainEntry(K key, V value) {
        return condition(actual -> anyMatch(actual,
                        entry -> entryMatches(entry, key, value)), false,
                "map not to contain expected entry",
                "map contained expected entry");
    }

    static <K, V> PreservingCondition<Map<? super K, ? super V>>
            containsAllEntriesOf(Map<? extends K, ? extends V> expected) {
        return membership(validate(expected), true, true,
                "map to contain all expected entries",
                "map did not contain all expected entries");
    }

    static <K, V> PreservingCondition<Map<? super K, ? super V>>
            doesNotContainAllEntriesOf(
                    Map<? extends K, ? extends V> expected) {
        return membership(validate(expected), true, false,
                "map not to contain all expected entries",
                "map contained all expected entries");
    }

    static <K, V> PreservingCondition<Map<? super K, ? super V>>
            containsAnyEntriesOf(Map<? extends K, ? extends V> expected) {
        return membership(validate(expected), false, true,
                "map to contain any expected entry",
                "map did not contain any expected entry");
    }

    static <K, V> PreservingCondition<Map<? super K, ? super V>>
            containsNoEntriesOf(Map<? extends K, ? extends V> expected) {
        return membership(validate(expected), false, false,
                "map to contain none of the expected entries",
                "map contained an expected entry");
    }

    static <K, V> PreservingCondition<Map<? super K, ? super V>>
            containsExactlyEntriesOf(
                    Map<? extends K, ? extends V> expected) {
        return exact(validateExact(expected), true,
                "map to contain exactly the expected entries",
                "map did not contain exactly the expected entries");
    }

    static <K, V> PreservingCondition<Map<? super K, ? super V>>
            doesNotContainExactlyEntriesOf(
                    Map<? extends K, ? extends V> expected) {
        return exact(validateExact(expected), false,
                "map not to contain exactly the expected entries",
                "map contained exactly the expected entries");
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
                    : anyMatch(actual, entry -> matchesAny(entry, positions));
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

    private static boolean matchesAny(Map.Entry<?, ?> actual,
            List<Map.Entry<?, ?>> expected) {
        return expected.stream().anyMatch(entry -> entryMatches(actual, entry));
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
        int expectedSize = expected.size();
        if (actualSize != expectedSize) {
            return false;
        }
        if (actualSize == 0) {
            return true;
        }

        List<Map.Entry<?, ?>> remaining = entries(expected);
        for (Map.Entry<?, ?> actualEntry : actual.entrySet()) {
            boolean found = false;
            var candidates = remaining.iterator();
            while (candidates.hasNext()) {
                if (entryMatches(actualEntry, candidates.next())) {
                    candidates.remove();
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static List<Map.Entry<?, ?>> entries(Map<?, ?> map) {
        List<Map.Entry<?, ?>> entries = new ArrayList<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            entries.add(entry);
        }
        return entries;
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
        requireNonNull(expected, "expected entries must not be null");
        if (expected.isEmpty()) {
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

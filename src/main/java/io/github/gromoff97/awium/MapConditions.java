package io.github.gromoff97.awium;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

final class MapConditions {

    private MapConditions() {
    }

    static <K> PreservingCondition<Map<? super K, ?>> containsKey(K expected) {
        return condition(actual -> anyMatch(actual, entry ->
                        ValueEquality.equal(entry.getKey(), expected)), true,
                "map to contain expected key",
                "map did not contain expected key");
    }

    static <K> PreservingCondition<Map<? super K, ?>> doesNotContainKey(
            K expected) {
        return condition(actual -> anyMatch(actual, entry ->
                        ValueEquality.equal(entry.getKey(), expected)), false,
                "map not to contain expected key",
                "map contained expected key");
    }

    static <V> PreservingCondition<Map<?, ? super V>> containsValue(
            V expected) {
        return condition(actual -> anyMatch(actual, entry ->
                        ValueEquality.equal(entry.getValue(), expected)), true,
                "map to contain expected value",
                "map did not contain expected value");
    }

    static <V> PreservingCondition<Map<?, ? super V>> doesNotContainValue(
            V expected) {
        return condition(actual -> anyMatch(actual, entry ->
                        ValueEquality.equal(entry.getValue(), expected)), false,
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
        ConditionRuntime<M, M> runtime = new ConditionRuntime<>(actual -> {
            if (actual == null) {
                return Evaluation.unsatisfied("map was null");
            }
            return matches.test(actual) == positive
                    ? Evaluation.satisfied(actual)
                    : Evaluation.unsatisfied(mismatch);
        }, () -> description, null);
        return new PreservingCondition<>(runtime);
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
        for (Map.Entry<?, ?> entry : expected) {
            if (entryMatches(actual, entry)) {
                return true;
            }
        }
        return false;
    }

    private static boolean allFound(Map<?, ?> actual,
            List<Map.Entry<?, ?>> expected) {
        boolean[] found = new boolean[expected.size()];
        int remaining = found.length;
        if (remaining == 0) {
            return true;
        }
        for (Map.Entry<?, ?> actualEntry : actual.entrySet()) {
            for (int index = 0; index < expected.size(); index++) {
                if (!found[index]
                        && entryMatches(actualEntry, expected.get(index))) {
                    found[index] = true;
                    remaining--;
                }
            }
            if (remaining == 0) {
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

        List<Map.Entry<?, ?>> positions = entries(expected);
        boolean[] consumed = new boolean[positions.size()];
        int matched = 0;
        for (Map.Entry<?, ?> actualEntry : actual.entrySet()) {
            boolean found = false;
            for (int index = 0; index < positions.size(); index++) {
                if (!consumed[index]
                        && entryMatches(actualEntry, positions.get(index))) {
                    consumed[index] = true;
                    matched++;
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return matched == expectedSize && matched == positions.size();
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
        return ValueEquality.equal(actual.getKey(), expected.getKey())
                && ValueEquality.equal(
                        actual.getValue(), expected.getValue());
    }

    private static boolean entryMatches(Map.Entry<?, ?> actual,
            Object expectedKey, Object expectedValue) {
        return ValueEquality.equal(actual.getKey(), expectedKey)
                && ValueEquality.equal(actual.getValue(), expectedValue);
    }

    private static <K, V> Map<? extends K, ? extends V> validate(
            Map<? extends K, ? extends V> expected) {
        Objects.requireNonNull(expected, "expected entries must not be null");
        if (expected.isEmpty()) {
            throw new IllegalArgumentException(
                    "expected entries must not be empty");
        }
        return expected;
    }

    private static <K, V> Map<? extends K, ? extends V> validateExact(
            Map<? extends K, ? extends V> expected) {
        return Objects.requireNonNull(
                expected, "expected entries must not be null");
    }
}

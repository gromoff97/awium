package io.github.gromoff97.assertility;

import java.util.Map;

final class MapSupport {
    private MapSupport() {
    }

    static <K, V> void assertContainsEntry(Map<K, V> actual, K key, V expectedValue) {
        if (!hasEntry(actual, key, expectedValue)) {
            throw new AssertionError(
                    "map did not contain the expected key association and recursive value");
        }
    }

    static <K, V> void assertDoesNotContainEntry(Map<K, V> actual, K key, V value) {
        if (hasEntry(actual, key, value)) {
            throw new AssertionError(
                    "map contained the unexpected key association and recursive value");
        }
    }

    static <K, V> void assertContainsAllEntries(
            Map<K, V> actual, Map<? extends K, ? extends V> expected) {
        for (var entry : expected.entrySet()) {
            if (!hasEntry(actual, entry.getKey(), entry.getValue())) {
                throw new AssertionError(
                        "map did not contain all expected key associations and recursive values");
            }
        }
    }

    static <K, V> void assertContainsExactlyEntries(
            Map<K, V> actual, Map<? extends K, ? extends V> expected) {
        if (actual.size() != expected.size()) {
            throw new AssertionError("map size differed from the exact expected content");
        }
        assertContainsAllEntries(actual, expected);
    }

    private static <K, V> boolean hasEntry(Map<K, V> actual, K key, V expectedValue) {
        if (!actual.containsKey(key)) {
            return false;
        }
        try {
            AssertJSupport.assertRecursiveEqual(actual.get(key), expectedValue);
            return true;
        } catch (AssertionError mismatch) {
            return false;
        }
    }
}

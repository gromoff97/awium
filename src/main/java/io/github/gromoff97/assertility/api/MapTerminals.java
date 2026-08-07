package io.github.gromoff97.assertility.api;

import java.util.Map;

public interface MapTerminals<K, V, M extends Map<K, V>, R>
        extends ObjectTerminals<M, R> {
    R isEmpty();

    R isNotEmpty();

    R hasSize(int size);

    R hasSizeGreaterThan(int size);

    R hasSizeGreaterThanOrEqualTo(int size);

    R hasSizeLessThan(int size);

    R hasSizeLessThanOrEqualTo(int size);

    R containsKey(K key);

    R doesNotContainKey(K key);

    R containsEntry(K key, V value);

    R doesNotContainEntry(K key, V value);

    R containsAllEntriesOf(Map<? extends K, ? extends V> expected);

    R containsExactlyInAnyOrderEntriesOf(Map<? extends K, ? extends V> expected);
}

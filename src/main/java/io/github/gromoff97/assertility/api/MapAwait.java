package io.github.gromoff97.assertility.api;

import java.util.Map;

public interface MapAwait<K, V, M extends Map<K, V>> extends MapTerminals<K, V, M, M> {
    MapTerminals<K, V, M, M> as(String description);

    MapTerminals<K, V, M, M> as(String format, Object... args);
}

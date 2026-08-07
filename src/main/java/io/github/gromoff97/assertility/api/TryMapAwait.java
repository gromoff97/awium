package io.github.gromoff97.assertility.api;

import io.github.gromoff97.assertility.AwaitResult;

import java.util.Map;

public interface TryMapAwait<K, V, M extends Map<K, V>>
        extends MapTerminals<K, V, M, AwaitResult<M>> {
}

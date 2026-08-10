package io.github.gromoff97.awium;

import java.time.Duration;
import java.util.Map;

public sealed interface MapAwait<K, V, M extends Map<K, V>>
        extends MapUntil<K, V, M> permits MapStageAdapters.MapInitialStage {

    AfterEvery<K, V, M> every(Duration interval);

    AfterUpTo<K, V, M> upTo(Duration timeout);

    MapUntil<K, V, M> stableFor(Duration stability);

    sealed interface AfterEvery<K, V, M extends Map<K, V>>
            extends AfterUpTo<K, V, M>
            permits MapStageAdapters.MapAfterEveryStage {

        AfterUpTo<K, V, M> upTo(Duration timeout);
    }

    sealed interface AfterUpTo<K, V, M extends Map<K, V>>
            extends MapUntil<K, V, M>
            permits AfterEvery, MapStageAdapters.MapAfterUpToStage {

        MapUntil<K, V, M> stableFor(Duration stability);
    }
}

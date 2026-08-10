package io.github.gromoff97.awium;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

final class MapStageAdapters {

abstract static class MapTerminals<K, V, M extends Map<K, V>>
        extends ObjectStageAdapters.ObjectTerminals<M> {

    MapTerminals(AwaitChain<M> chain) {
        super(chain);
    }

    public final M until(StructuralCondition condition) {
        Objects.requireNonNull(condition, "condition must not be null");
        chain.config().validatePair();
        return chain.execute(ConditionAdapters.structural(
                condition, "map", Map::size));
    }

    public final M until(ExplainedStructuralCondition condition) {
        Objects.requireNonNull(condition, "condition must not be null");
        chain.config().validatePair();
        return chain.execute(ConditionAdapters.structural(
                condition, "map", Map::size));
    }
}

abstract static class MapConfigStages<K, V, M extends Map<K, V>>
        extends MapTerminals<K, V, M> {

    MapConfigStages(AwaitChain<M> chain) {
        super(chain);
    }

    public final MapUntil<K, V, M> stableFor(Duration stability) {
        return new MapTerminalStage<>(chain.withStableFor(stability));
    }
}

static final class MapInitialStage<K, V, M extends Map<K, V>>
        extends MapConfigStages<K, V, M> implements MapAwait<K, V, M> {

    MapInitialStage(AwaitChain<M> chain) {
        super(chain);
    }

    @Override
    public MapAwait.AfterEvery<K, V, M> every(Duration interval) {
        return new MapAfterEveryStage<>(chain.withEvery(interval));
    }

    @Override
    public MapAwait.AfterUpTo<K, V, M> upTo(Duration timeout) {
        return new MapAfterUpToStage<>(chain.withUpTo(timeout));
    }
}

static final class MapAfterEveryStage<K, V, M extends Map<K, V>>
        extends MapConfigStages<K, V, M>
        implements MapAwait.AfterEvery<K, V, M> {

    MapAfterEveryStage(AwaitChain<M> chain) {
        super(chain);
    }

    @Override
    public MapAwait.AfterUpTo<K, V, M> upTo(Duration timeout) {
        return new MapAfterUpToStage<>(chain.withUpTo(timeout));
    }
}

static final class MapAfterUpToStage<K, V, M extends Map<K, V>>
        extends MapConfigStages<K, V, M>
        implements MapAwait.AfterUpTo<K, V, M> {

    MapAfterUpToStage(AwaitChain<M> chain) {
        super(chain);
    }
}

static final class MapTerminalStage<K, V, M extends Map<K, V>>
        extends MapTerminals<K, V, M> implements MapUntil<K, V, M> {

    MapTerminalStage(AwaitChain<M> chain) {
        super(chain);
    }
}
}

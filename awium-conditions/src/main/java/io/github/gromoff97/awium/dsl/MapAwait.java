package io.github.gromoff97.awium.dsl;

import io.github.gromoff97.awium.dsl.AwaitCondition.MapValueCondition;
import io.github.gromoff97.awium.execution.AwaitHandler;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

import static java.util.Map.Entry;

public final class MapAwait<OBSERVED extends Map<?, ?>, ENTRY extends Entry<?, ?>, VALUE>
        extends SelectionAwait<OBSERVED, ENTRY> {

    MapAwait(io.github.gromoff97.awium.ValueAwait<OBSERVED> waiting) {
        super(waiting);
    }

    @Override
    public MapAwait<OBSERVED, ENTRY, VALUE> handling(Supplier<? extends AwaitHandler<OBSERVED>> factory) {
        return new MapAwait<>(super.handling(factory));
    }

    @Override
    public MapAwait<OBSERVED, ENTRY, VALUE> every(Duration interval) {
        return new MapAwait<>(super.every(interval));
    }

    @Override
    public MapAwait<OBSERVED, ENTRY, VALUE> upTo(Duration timeout) {
        return new MapAwait<>(super.upTo(timeout));
    }

    @Override
    public MapAwait<OBSERVED, ENTRY, VALUE> persisting(Duration persistence) {
        return new MapAwait<>(super.persisting(persistence));
    }

    public VALUE until(MapValueCondition<? super OBSERVED> condition) {
        return super.until(ConditionBinding.<OBSERVED, VALUE>bind(condition));
    }
}

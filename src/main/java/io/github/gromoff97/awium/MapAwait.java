package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.MapValueCondition;
import io.github.gromoff97.awium.internal.WaitConfiguration;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static java.util.Map.Entry;

public final class MapAwait<OBSERVED extends Map<?, ?>, ENTRY extends Entry<?, ?>, VALUE>
        extends SelectionAwait<OBSERVED, ENTRY> {

    MapAwait(Callable<? extends OBSERVED> source, WaitConfiguration configuration) {
        super(source, configuration);
    }

    @Override
    public MapAwait<OBSERVED, ENTRY, VALUE> every(Duration interval) {
        return new MapAwait<>(source, configuration.withEvery(interval));
    }

    @Override
    public MapAwait<OBSERVED, ENTRY, VALUE> upTo(Duration timeout) {
        return new MapAwait<>(source, configuration.withUpTo(timeout));
    }

    @Override
    public MapAwait<OBSERVED, ENTRY, VALUE> persisting(Duration persistence) {
        return new MapAwait<>(source, configuration.withPersistence(persistence));
    }

    @Override
    MapAwait<OBSERVED, ENTRY, VALUE> usingTime(LongSupplier clock, LongConsumer parker) {
        return new MapAwait<>(source, configuration.withTime(clock, parker));
    }

    public VALUE until(MapValueCondition<? super OBSERVED> condition) {
        return complete(condition);
    }
}

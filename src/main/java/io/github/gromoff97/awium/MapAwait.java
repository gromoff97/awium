package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.MapValueCondition;
import io.github.gromoff97.awium.internal.WaitConfiguration;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static io.github.gromoff97.awium.Condition.required;

/**
 * Immutable wait configuration supporting both entry and value selection from a map.
 *
 * @param <OBSERVED> complete map returned by the source
 * @param <ENTRY> entry selected from the map
 * @param <VALUE> value selected from the map
 */
public final class MapAwait<OBSERVED extends Map<?, ?>, ENTRY extends Map.Entry<?, ?>, VALUE>
        extends SelectionAwait<OBSERVED, ENTRY> {

    MapAwait(Callable<? extends OBSERVED> source, WaitConfiguration configuration) {
        super(source, configuration);
    }

    @Override
    public MapAwait<OBSERVED, ENTRY, VALUE> every(Duration interval) {
        return reconfigured(configuration.withEvery(interval));
    }

    @Override
    public MapAwait<OBSERVED, ENTRY, VALUE> upTo(Duration timeout) {
        return reconfigured(configuration.withUpTo(timeout));
    }

    @Override
    public MapAwait<OBSERVED, ENTRY, VALUE> persisting(Duration persistence) {
        return reconfigured(configuration.withPersistence(persistence));
    }

    @Override
    public MapAwait<OBSERVED, ENTRY, VALUE> usingTime(LongSupplier clock, LongConsumer parker) {
        return reconfigured(configuration.withTime(clock, parker));
    }

    private MapAwait<OBSERVED, ENTRY, VALUE> reconfigured(WaitConfiguration configuration) {
        return new MapAwait<>(source, configuration);
    }

    public VALUE until(MapValueCondition<? super OBSERVED> condition) {
        return complete(ConditionSupport.bind(required(condition)));
    }
}

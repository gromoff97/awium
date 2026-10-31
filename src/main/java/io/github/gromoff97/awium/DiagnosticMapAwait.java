package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.MapValueCondition;
import io.github.gromoff97.awium.internal.WaitConfiguration;
import io.github.gromoff97.awium.result.AwaitResult;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static io.github.gromoff97.awium.Condition.required;

/**
 * Immutable diagnostic wait configuration supporting both entry and value selection from a map.
 *
 * @param <OBSERVED> complete map returned by the source
 * @param <ENTRY> entry selected from the map
 * @param <VALUE> value selected from the map
 */
public final class DiagnosticMapAwait<OBSERVED extends Map<?, ?>, ENTRY extends Map.Entry<?, ?>, VALUE>
        extends DiagnosticSelectionAwait<OBSERVED, ENTRY> {

    DiagnosticMapAwait(Callable<? extends OBSERVED> source, WaitConfiguration configuration) {
        super(source, configuration);
    }

    @Override
    public DiagnosticMapAwait<OBSERVED, ENTRY, VALUE> every(Duration interval) {
        return new DiagnosticMapAwait<>(source, configuration.withEvery(interval));
    }

    @Override
    public DiagnosticMapAwait<OBSERVED, ENTRY, VALUE> upTo(Duration timeout) {
        return new DiagnosticMapAwait<>(source, configuration.withUpTo(timeout));
    }

    @Override
    public DiagnosticMapAwait<OBSERVED, ENTRY, VALUE> persisting(Duration persistence) {
        return new DiagnosticMapAwait<>(source, configuration.withPersistence(persistence));
    }

    @Override
    DiagnosticMapAwait<OBSERVED, ENTRY, VALUE> usingTime(LongSupplier clock, LongConsumer parker) {
        return new DiagnosticMapAwait<>(source, configuration.withTime(clock, parker));
    }

    public AwaitResult<OBSERVED, VALUE> until(
            MapValueCondition<? super OBSERVED> condition) {
        return capture(ConditionSupport.bind(required(condition)));
    }

}

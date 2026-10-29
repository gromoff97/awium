package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.SelectedCondition;
import io.github.gromoff97.awium.internal.WaitConfiguration;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static io.github.gromoff97.awium.Condition.required;

/**
 * Immutable wait configuration for a source whose element can be selected.
 *
 * @param <OBSERVED> complete value returned by the source
 * @param <ELEMENT> value selected from an optional, collection, or map
 */
public sealed class SelectionAwait<OBSERVED, ELEMENT> extends ValueAwait<OBSERVED> permits MapAwait {

    SelectionAwait(Callable<? extends OBSERVED> source, WaitConfiguration configuration) {
        super(source, configuration);
    }

    @Override
    public SelectionAwait<OBSERVED, ELEMENT> every(Duration interval) {
        return reconfigured(configuration.withEvery(interval));
    }

    @Override
    public SelectionAwait<OBSERVED, ELEMENT> upTo(Duration timeout) {
        return reconfigured(configuration.withUpTo(timeout));
    }

    @Override
    public SelectionAwait<OBSERVED, ELEMENT> persisting(Duration persistence) {
        return reconfigured(configuration.withPersistence(persistence));
    }

    @Override
    public SelectionAwait<OBSERVED, ELEMENT> usingTime(LongSupplier clock, LongConsumer parker) {
        return reconfigured(configuration.withTime(clock, parker));
    }

    private SelectionAwait<OBSERVED, ELEMENT> reconfigured(WaitConfiguration configuration) {
        return new SelectionAwait<>(source, configuration);
    }

    public ELEMENT until(SelectedCondition<? super OBSERVED> condition) {
        return complete(ConditionSupport.bind(required(condition)));
    }
}

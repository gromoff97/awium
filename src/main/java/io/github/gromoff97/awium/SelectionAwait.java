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
        return new SelectionAwait<>(source, configuration.withEvery(interval));
    }

    @Override
    public SelectionAwait<OBSERVED, ELEMENT> upTo(Duration timeout) {
        return new SelectionAwait<>(source, configuration.withUpTo(timeout));
    }

    @Override
    public SelectionAwait<OBSERVED, ELEMENT> persisting(Duration persistence) {
        return new SelectionAwait<>(source, configuration.withPersistence(persistence));
    }

    @Override
    public SelectionAwait<OBSERVED, ELEMENT> usingTime(LongSupplier clock, LongConsumer parker) {
        return new SelectionAwait<>(source, configuration.withTime(clock, parker));
    }

    public ELEMENT until(SelectedCondition<? super OBSERVED> condition) {
        return complete(ConditionSupport.bind(required(condition)));
    }
}

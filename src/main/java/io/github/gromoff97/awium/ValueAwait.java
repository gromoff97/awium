package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.ApplicableCondition;
import io.github.gromoff97.awium.Condition.Captured;
import io.github.gromoff97.awium.Condition.PreservesObservation;
import io.github.gromoff97.awium.Condition.Reference;
import io.github.gromoff97.awium.internal.WaitConfiguration;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static io.github.gromoff97.awium.Condition.Typed;

public sealed class ValueAwait<OBSERVED> extends AwaitSupport<OBSERVED> permits SelectionAwait {

    ValueAwait(Callable<? extends OBSERVED> source, WaitConfiguration configuration) {
        super(source, configuration);
    }

    public ValueAwait<OBSERVED> every(Duration interval) {
        return new ValueAwait<>(source, configuration.withEvery(interval));
    }

    public ValueAwait<OBSERVED> upTo(Duration timeout) {
        return new ValueAwait<>(source, configuration.withUpTo(timeout));
    }

    public ValueAwait<OBSERVED> persisting(Duration persistence) {
        return new ValueAwait<>(source, configuration.withPersistence(persistence));
    }

    ValueAwait<OBSERVED> usingTime(LongSupplier clock, LongConsumer parker) {
        return new ValueAwait<>(source, configuration.withTime(clock, parker));
    }

    public <CHECK extends ApplicableCondition<? super OBSERVED, ? super Reference<? super OBSERVED>> & PreservesObservation>
            OBSERVED until(CHECK condition) {
        return complete(condition);
    }

    public <RESULT> RESULT until(Typed<? super OBSERVED, ? extends RESULT> condition) {
        return complete(condition);
    }

    public List<OBSERVED> until(
            Captured<? extends ApplicableCondition<? super OBSERVED, ? super Reference<? super OBSERVED>>> condition) {
        return complete(condition);
    }
}

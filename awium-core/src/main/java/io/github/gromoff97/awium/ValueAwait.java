package io.github.gromoff97.awium;

import io.github.gromoff97.awium.AwaitCondition.ApplicableCondition;
import io.github.gromoff97.awium.AwaitCondition.CollectingCondition;
import io.github.gromoff97.awium.AwaitCondition.PreservesObservation;
import io.github.gromoff97.awium.AwaitCondition.Reference;
import io.github.gromoff97.awium.internal.WaitConfiguration;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;

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

    public OBSERVED until(PreservesObservation<? super OBSERVED, ? super Reference<? super OBSERVED>> condition) {
        return complete(condition);
    }

    public <RESULT> RESULT until(Condition<? super OBSERVED, ? extends RESULT> condition) {
        return complete(condition);
    }

    public List<OBSERVED> until(
            CollectingCondition<? extends ApplicableCondition<? super OBSERVED, ? super Reference<? super OBSERVED>>> condition) {
        return complete(condition);
    }
}

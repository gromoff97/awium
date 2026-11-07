package io.github.gromoff97.awium.dsl;

import io.github.gromoff97.awium.Condition.Reference;
import io.github.gromoff97.awium.dsl.AwaitCondition.ApplicableCondition;
import io.github.gromoff97.awium.dsl.AwaitCondition.CollectingCondition;
import io.github.gromoff97.awium.dsl.AwaitCondition.PreservesObservation;
import io.github.gromoff97.awium.execution.AwaitHandler;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

public sealed class ValueAwait<OBSERVED> extends io.github.gromoff97.awium.ValueAwait<OBSERVED> permits SelectionAwait {

    ValueAwait(io.github.gromoff97.awium.ValueAwait<OBSERVED> waiting) {
        super(waiting);
    }

    @Override
    public ValueAwait<OBSERVED> handling(Supplier<? extends AwaitHandler<OBSERVED>> factory) {
        return new ValueAwait<>(super.handling(factory));
    }

    @Override
    public ValueAwait<OBSERVED> every(Duration interval) {
        return new ValueAwait<>(super.every(interval));
    }

    @Override
    public ValueAwait<OBSERVED> upTo(Duration timeout) {
        return new ValueAwait<>(super.upTo(timeout));
    }

    @Override
    public ValueAwait<OBSERVED> persisting(Duration persistence) {
        return new ValueAwait<>(super.persisting(persistence));
    }

    public OBSERVED until(PreservesObservation<? super OBSERVED, ? super Reference<? super OBSERVED>> condition) {
        return super.until(ConditionBinding.<OBSERVED, OBSERVED>bind(condition));
    }

    public List<OBSERVED> until(
            CollectingCondition<? extends ApplicableCondition<? super OBSERVED, ? super Reference<? super OBSERVED>>> condition) {
        return super.until(ConditionBinding.<OBSERVED, List<OBSERVED>>bind(condition));
    }
}
